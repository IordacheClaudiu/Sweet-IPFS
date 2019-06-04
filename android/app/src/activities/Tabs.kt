package activities

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.text.InputType
import android.util.SparseArray
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import com.google.gson.Gson
import com.tbruyelle.rxpermissions2.RxPermissions
import fragments.FeedFragment
import fragments.PeersFragment
import io.ipfs.api.Peer
import io.ipfs.multiaddr.MultiAddress
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.android.synthetic.main.activity_tabbar.*
import kotlinx.android.synthetic.main.toolbar_main.*
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.info
import org.jetbrains.anko.uiThread
import ro.uaic.info.ipfs.R
import services.ForegroundService
import services.ipfs
import services.ipfsDaemon
import utils.Constants.INTENT_USER_HASH
import utils.clipboard
import java.io.IOException


class TabsAdapter(fm: FragmentManager) : FragmentPagerAdapter(fm) {

    private var registeredFragments = SparseArray<Fragment>()

    override fun getItem(position: Int): Fragment {
        return when (position) {
            0 -> {
                FeedFragment()
            }
            1 -> PeersFragment()
            else -> {
                return FeedFragment()
            }
        }
    }

    override fun getCount(): Int {
        return 2
    }

    override fun getPageTitle(position: Int): CharSequence {
        return when (position) {
            0 -> "Feed"
            1 -> "Peers"
            else -> {
                return "Third Tab"
            }
        }
    }

    override fun instantiateItem(container: ViewGroup , position: Int): Any {
        val fragment = super.instantiateItem(container , position) as Fragment
        registeredFragments.put(position , fragment)
        return fragment
    }

    override fun destroyItem(container: ViewGroup , position: Int , `object`: Any) {
        registeredFragments.remove(position)
        super.destroyItem(container , position , `object`)
    }

    fun registeredFragment(position: Int): Fragment {
        return registeredFragments.get(position)
    }
}

class TabsActivity : AppCompatActivity() , AnkoLogger , FeedFragment.FeedFragmentListener , PeersFragment.PeersFragmentListener {

    private val ctx = this as Context
    private lateinit var tabsAdapter: TabsAdapter

    // Request Codes
    private val READ_DOCUMENT_REQUEST_CODE = 101
    private val IMAGE_CAPTURE_REQUEST_CODE = 102

    private val notImplemented = { AlertDialog.Builder(this).setMessage("This feature is not yet implemented. Sorry").show(); true }

    private var mService: ForegroundService? = null
    private var mBound: Boolean = false
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName , service: IBinder) {
            // We've bound to LocalService, cast the IBinder and get ForegroundService instance
            val binder = service as ForegroundService.ForegroundBinder
            mService = binder.getService()
            mService?.let {
                it.loadingPeersObservable.observeOn(AndroidSchedulers.mainThread()).subscribe {
                    when (it) {
                        true -> {
                            progressBar.visibility = View.VISIBLE
                            progressTextView.visibility = View.VISIBLE
                        }
                        false -> {
                            progressBar.visibility = View.GONE
                            progressTextView.visibility = View.GONE
                        }
                    }
                }

                it.publicResourcesObservable?.observeOn(AndroidSchedulers.mainThread()).subscribe {
                    val position = viewpager_main.currentItem
                    val fragment = tabsAdapter.registeredFragment(position)
                    if (fragment is FeedFragment) {
                        fragment.add(it)
                    }
                }
                it.privateResourceObservable?.observeOn(AndroidSchedulers.mainThread()).subscribe {
                    val analysisJSON = Gson().toJson(it)
                    startActivity(ImageRekognitionIntent(analysisJSON))
                }
            }

            mBound = true
            setupLocationManager()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mService = null
            mBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tabbar)
        setSupportActionBar(toolbar)
        tabsAdapter = TabsAdapter(supportFragmentManager)
        viewpager_main.adapter = tabsAdapter
        tabs_main.setupWithViewPager(viewpager_main)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.settings -> {
            val view = findViewById<View>(R.id.settings)
            PopupMenu(this , view).apply {
                menu.apply {
                    add(getString(R.string.menu_garbage_collect)).setOnMenuItemClickListener {
                        true.also {
                            doAsync {
                                ipfs.repo.gc()
                                uiThread {
                                    AlertDialog.Builder(this@TabsActivity)
                                            .setMessage(getString(R.string.garbage_collected)).show()
                                }
                            }
                        }
                    }

                    addSubMenu(getString(R.string.menu_swarm)).apply {
                        add(getString(R.string.menu_swarm_connect)).setOnMenuItemClickListener {
                            AlertDialog.Builder(this@TabsActivity).apply {
                                val txtView = EditText(this@TabsActivity).apply {
                                    inputType = InputType.TYPE_CLASS_TEXT
                                    setView(this)
                                }
                                setPositiveButton(getString(R.string.apply)) { _ , _ ->
                                    val txtInput = txtView.text.toString()
                                    if (txtInput.isBlank()) {
                                        return@setPositiveButton
                                    }
                                    val nodeAddress: MultiAddress
                                    try {
                                        nodeAddress = MultiAddress(txtInput)
                                    } catch (e: IllegalStateException) {
                                        info { "Swarm add peer error:" + e.localizedMessage }
                                        return@setPositiveButton
                                    }
                                    doAsync({
                                        info { "Swarm add peer error: " + it.localizedMessage }
                                    } , {
                                        ipfs.swarm.connect(nodeAddress)
                                        info { "Swarm add peer success." }
                                    })
                                }.show()
                            }
                            true
                        }
                    }

                    addSubMenu(getString(R.string.menu_identity)).apply {
                        add(getString(R.string.menu_identity_peerid)).setOnMenuItemClickListener {
                            val id = this@TabsActivity !!.ipfsDaemon.config.getAsJsonObject("Identity").getAsJsonPrimitive("PeerID").asString
                            AlertDialog.Builder(this@TabsActivity).apply {
                                setTitle(getString(R.string.title_peerid))
                                setMessage(id)
                                setPositiveButton(getString(R.string.copy)) { _ , _ -> }
                                setNeutralButton(getString(R.string.close)) { _ , _ -> }
                            }.show().apply {
                                getButton(AlertDialog.BUTTON_POSITIVE)
                                        .setOnClickListener { clipboard(id) }
                            }; true
                        }
                        add(getString(R.string.menu_identity_privatekey)).setOnMenuItemClickListener {
                            val key = this@TabsActivity !!.ipfsDaemon.config.getAsJsonObject("Identity").getAsJsonPrimitive("PrivKey").asString
                            AlertDialog.Builder(this@TabsActivity).apply {
                                setTitle(getString(R.string.title_privatekey))
                                setMessage(key)
                                setPositiveButton(getString(R.string.copy)) { _ , _ -> }
                                setNeutralButton(getString(R.string.close)) { _ , _ -> }
                            }.show().apply {
                                getButton(AlertDialog.BUTTON_POSITIVE)
                                        .setOnClickListener { clipboard(key) }
                            }; true
                        }
                    }

                    add(getString(R.string.menu_peers)).setOnMenuItemClickListener {
                        doAsync({
                            info { "Swarm Peers error." }
                        } , {
                            var representation = "No peers."
                            var peers = ipfs.swarm.peers()
                            if (peers.isNotEmpty()) {
                                representation = peers.joinToString(separator = "\n" , transform = {
                                    it.id.toString()
                                })
                            }
                            info { "Swarm Peers success." }
                            uiThread {
                               AlertDialog.Builder(this@TabsActivity).apply {
                                    setTitle(getString(R.string.menu_peers))
                                    setMessage(representation)
                                    setNeutralButton(getString(R.string.close)) { _ , _ -> }
                                }.show()
                            }
                        });true
                    }

                    add(getString(R.string.menu_others)).setOnMenuItemClickListener {
                        doAsync({} , {
                            ipfs.version()
                            val addresses = this@TabsActivity !!.ipfsDaemon.config.getAsJsonObject("Addresses")
                            AlertDialog.Builder(this@TabsActivity).apply {
                                setTitle(getString(R.string.title_others))
                                setMessage("""
                                            ${getString(R.string.others_goipfs_version)}: $it
                                            ${getString(R.string.others_api_address)}: ${addresses.getAsJsonPrimitive("API").asString}
                                            ${getString(R.string.others_gateway_address)}: ${addresses.getAsJsonPrimitive("Gateway").asString}
                                        """.trimIndent())
                                setNeutralButton(getString(R.string.close)) { _ , _ -> }
                            }.show()
                        }); true
                    }

                    addSubMenu(getString(R.string.menu_bootstrap)).apply {
                        add(getString(R.string.menu_bootstrap_list_all)).setOnMenuItemClickListener {
                            doAsync {
                                try {
                                    var representation = "No bootstrap nodes."
                                    val multiAddresses = ipfs.bootstrap.list()
                                    if (multiAddresses.isNotEmpty()) {
                                        representation = multiAddresses.joinToString(separator = "\n")
                                    }
                                    info { "Bootstrap list success." }
                                    uiThread {
                                        AlertDialog.Builder(this@TabsActivity).apply {
                                            setTitle(getString(R.string.menu_bootstrap_list_all))
                                            setMessage(representation)
                                            setNeutralButton(getString(R.string.close)) { _ , _ -> }
                                        }.show()
                                    }
                                } catch (ex: IOException) {
                                    info { "Bootstrap list error." }
                                }
                            };true
                        }
                        add(getString(R.string.menu_bootstrap_add_node)).setOnMenuItemClickListener {
                            AlertDialog.Builder(this@TabsActivity).apply {
                                setTitle(getString(R.string.title_bootstrap_add_node))
                                val txtView = EditText(this@TabsActivity).apply {
                                    inputType = InputType.TYPE_CLASS_TEXT
                                    setView(this)
                                }
                                setPositiveButton(getString(R.string.apply)) { _ , _ ->
                                    val txtInput = txtView.text.toString()
                                    if (txtInput.isBlank()) {
                                        return@setPositiveButton
                                    }
                                    val nodeAddress: MultiAddress
                                    try {
                                        nodeAddress = MultiAddress(txtInput)
                                    } catch (e: IllegalStateException) {
                                        info { "Bootstrap add node error:" + e.localizedMessage }
                                        return@setPositiveButton
                                    }
                                    doAsync({
                                        info { "Bootstrap add node error." }
                                    } , {
                                        ipfs.bootstrap.add(nodeAddress)
                                        info { "Bootstrap add node success." }
                                    })
                                }
                                setNegativeButton(getString(R.string.cancel)) { _ , _ -> }
                            }.show(); true
                        }
                    }
                    addSubMenu(getString(R.string.menu_pubsub)).apply {
                        add(getString(R.string.menu_pubsub_list_rooms)).setOnMenuItemClickListener {
                            doAsync({ info { "PubSub list rooms error." } } , {
                                val list = ipfs.pubsub.ls()
                                val map = list as Map<String , List<String>>
                                val rooms = map["Strings"]
                                var text = "No rooms."
                                if (rooms != null && rooms.isNotEmpty()) {
                                    text = rooms.joinToString(separator = "\n")
                                }
                                info { "PubSub list rooms success." }
                                uiThread {
                                    AlertDialog.Builder(this@TabsActivity).apply {
                                        setTitle(getString(R.string.menu_pubsub_list_rooms))
                                        setMessage(text)
                                        setNeutralButton(getString(R.string.close)) { _ , _ -> }
                                    }.show()
                                }
                            }); true
                        }
                        add(getString(R.string.menu_pubsub_join_room)).setOnMenuItemClickListener {
                            AlertDialog.Builder(this@TabsActivity).apply {
                                setTitle(getString(R.string.menu_pubsub_join_room))
                                val txtView = EditText(this@TabsActivity).apply {
                                    inputType = InputType.TYPE_CLASS_TEXT
                                    setView(this)
                                }
                                setPositiveButton(getString(R.string.apply)) { _ , _ ->
                                    val room = txtView.text.toString()
                                    if (room.isBlank()) {
                                        return@setPositiveButton
                                    }
                                    doAsync({ info { "PubSub join room error." } } , {
                                        ipfs.pubsub.sub(room)
                                        info { "PubSub join room succedeed." }
                                    })
                                }
                                setNegativeButton(getString(R.string.cancel)) { _ , _ -> }
                            }.show(); true
                        }
                        add(getString(R.string.menu_pubsub_post_to_room)).setOnMenuItemClickListener {
                            AlertDialog.Builder(this@TabsActivity).apply {
                                val layout = LinearLayout(this@TabsActivity)
                                layout.orientation = LinearLayout.VERTICAL
                                val topicTextView = EditText(this@TabsActivity).apply {
                                    inputType = InputType.TYPE_CLASS_TEXT
                                    hint = "Enter room name"
                                }

                                layout.addView(topicTextView)
                                val messageTextView = EditText(this@TabsActivity).apply {
                                    inputType = InputType.TYPE_CLASS_TEXT
                                    hint = "Enter message"
                                }
                                layout.addView(messageTextView)
                                setView(layout)
                                setPositiveButton(getString(R.string.apply)) { _ , _ ->
                                    val room = topicTextView.text.toString()
                                    val message = messageTextView.text.toString()
                                    if (room.isNotBlank() && message.isNotBlank()) {
                                        doAsync({ info { "PubSub post room ${room} failed.\")" } } , {
                                            ipfs.pubsub.pub(room , message)
                                            info { "PubSub post to room $room succedeed." }
                                        })
                                    } else {

                                    }
                                }
                                setNegativeButton(getString(R.string.cancel)) { _ , _ -> }
                            }.show(); true
                        }
                    }
                }
            }.show()

            true
        }
        else -> {
            // If we got here, the user's action was not recognized.
            // Invoke the superclass to handle it.
            super.onOptionsItemSelected(item)
        }
    }

    override fun onStart() = super.onStart().also {
        Intent(this , ForegroundService::class.java).also { intent ->
            bindService(intent , connection , Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() = super.onStop().also {
        unbindService(connection)
        mBound = false
    }

    override fun onActivityResult(req: Int , res: Int , rdata: Intent?) {
        super.onActivityResult(req , res , rdata)
        if (res != RESULT_OK) return
        when (req) {
            IMAGE_CAPTURE_REQUEST_CODE -> {
                rdata?.extras?.also {
                    info { "Image taken" }
                    val imageBitmap = it.get("data") as Bitmap
                    mService?.send(imageBitmap)
                }
            }
            READ_DOCUMENT_REQUEST_CODE -> {
                rdata?.data?.also { uri ->
                    info { "Uri: $uri" }
                    mService?.send(uri)
                }
            }
        }
        when (req) {
            1 -> Intent(ctx , ShareActivity::class.java).apply {
                data = rdata?.data ?: return
                action = Intent.ACTION_SEND
                startActivity(this)
            }
        }
    }

    override fun feedFragmentOnAddFilePressed(fragment: FeedFragment) {
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            startActivityForResult(this , READ_DOCUMENT_REQUEST_CODE)
        }
    }

    override fun feedFragmentOnAddImagePressed(fragment: FeedFragment) {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            takePictureIntent.resolveActivity(packageManager)?.also {
                startActivityForResult(takePictureIntent , IMAGE_CAPTURE_REQUEST_CODE)
            }
        }
    }

    override fun feedFragmentOnAddTextPressed(fragment: FeedFragment) {
        AlertDialog.Builder(ctx).apply {
            setTitle(getString(R.string.title_add_text))
            val txt = EditText(ctx).apply {
                inputType = InputType.TYPE_CLASS_TEXT
                setView(this)
            }
            setPositiveButton(getString(R.string.apply)) { _ , _ ->
                mService?.send(txt.text.toString())
            }
            setNegativeButton(getString(R.string.cancel)) { _ , _ -> }
        }.show()

    }

    override fun peersFragmentOnPeerPressed(fragment: PeersFragment , peer: Peer) {
        val intent = Intent(this , ResourcesActivity::class.java).apply {
            putExtra(INTENT_USER_HASH , peer.id.toBase58())
        }
        startActivity(intent)
    }

    private fun setupLocationManager() {
        RxPermissions(this)
                .requestEach(Manifest.permission.ACCESS_FINE_LOCATION)
                .subscribe { permission ->
                    if (permission.granted) {
                        mService?.startTracking()
                    } else if (permission.shouldShowRequestPermissionRationale) {
                        // Denied permission without ask never again
                    } else {
                        // Denied permission with ask never again
                        // Need to go to the settings
                    }
                }.dispose()
    }

}
