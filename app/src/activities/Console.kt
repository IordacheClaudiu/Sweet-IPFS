package activities

import adapters.resources.ResourcesRecyclerAdapter
import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_SEND
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import application.async
import application.clipboard
import application.ipfs
import io.ipfs.api.Peer
import io.ipfs.multiaddr.MultiAddress
import kotlinx.android.synthetic.main.activity_console.*
import models.IIpfsResource
import models.PeerDTO
import org.jetbrains.anko.*
import ro.uaic.info.ipfs.R
import services.ipfsDaemon
import utils.Constants
import utils.Constants.IPFS_PUB_SUB_CHANNEL
import utils.ResourceReceiver
import utils.ResourceSender
import java.util.function.Consumer
import java.util.stream.Collectors

class ConsoleActivity : AppCompatActivity() , AnkoLogger {

    private val ctx = this as Context

    // UI
    private lateinit var linearLayoutManager: LinearLayoutManager
    private lateinit var adapter: ResourcesRecyclerAdapter

    // Request Codes
    private val FINE_LOCATION_REQUEST_CODE = 100
    private val READ_DOCUMENT_REQUEST_CODE = 101
    private val IMAGE_CAPTURE_REQUEST_CODE = 102

    // IPFS
    private val username by lazy {
        defaultSharedPreferences.getString(Constants.SHARED_PREF_USERNAME , null)
    }
    private val device by lazy {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        listOf(manufacturer , model).joinToString(",")
    }
    private val os by lazy {
        val version = Build.VERSION.SDK_INT
        val versionRelease = Build.VERSION.RELEASE
        listOf(version , versionRelease).joinToString(",")
    }
    private val notImplemented = { AlertDialog.Builder(ctx).setMessage("This feature is not yet implemented. Sorry").show(); true }
    private var resourceSender: ResourceSender? = null
    private var resourceReceiver: ResourceReceiver? = null

    // Location
    private val locationManager by lazy { getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    private val locationProvider = LocationManager.GPS_PROVIDER
    private var lastKnownLocation: Location? = null
    private val locationListener = object : LocationListener {

        override fun onLocationChanged(location: Location) {
            info { location }
            lastKnownLocation = location
            if (lastKnownLocation != null) {
                resourceSender?.send(IPFS_PUB_SUB_CHANNEL , lastKnownLocation !! , null, null)
            }
        }

        override fun onStatusChanged(provider: String , status: Int , extras: Bundle) {
            info { "onStatusChanged: $status" }
        }

        override fun onProviderEnabled(provider: String) {
            info { "onProviderEnabled: $provider" }
        }

        override fun onProviderDisabled(provider: String) {
            info { "onProviderDisabled: $provider" }
        }
    }

    override fun onCreate(state: Bundle?) = super.onCreate(state).also {
        setContentView(R.layout.activity_console)
        setupRecyclerView()
        setupActionBtn(notImplemented)
        setupConfigBtn()
        setupFloatingBtns()
    }

    override fun onResume() {
        super.onResume()
        setupLocationManager()
        setupCurrentPeer()
    }

    override fun onActivityResult(req: Int , res: Int , rdata: Intent?) {
        super.onActivityResult(req , res , rdata)
        if (res != RESULT_OK) return
        when (req) {
            IMAGE_CAPTURE_REQUEST_CODE -> {
                rdata?.extras?.also {
                    info { "Image taken" }
                    val imageBitmap = it.get("data") as Bitmap
                    resourceSender?.send(IPFS_PUB_SUB_CHANNEL , imageBitmap , null , null)
                }
            }
            READ_DOCUMENT_REQUEST_CODE -> {
                rdata?.data?.also { uri ->
                    info { "Uri: $uri" }
                    resourceSender?.send(IPFS_PUB_SUB_CHANNEL , uri , null, null)
                }
            }
        }
        when (req) {
            1 -> Intent(ctx , ShareActivity::class.java).apply {
                data = rdata?.data ?: return
                action = ACTION_SEND
                startActivity(this)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int ,
                                            permissions: Array<String> , grantResults: IntArray) {
        when (requestCode) {
            FINE_LOCATION_REQUEST_CODE -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    try {
                        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER , 2000 , 3f , locationListener)
                    } catch (e: SecurityException) {
                        error { e }
                    }
                } else {
                    error { "Location service not granted" }
                }
                return
            }

            else -> {
                // Ignore all other requests.
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        resourceReceiver?.unsubscribeFrom(IPFS_PUB_SUB_CHANNEL)
        locationManager.removeUpdates(locationListener)
    }

    private fun setupRecyclerView() {
        linearLayoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = linearLayoutManager
        val resources: MutableList<IIpfsResource> = mutableListOf()
        adapter = ResourcesRecyclerAdapter(ipfs , resources)
        recyclerView.adapter = adapter
    }

    private fun setupLocationManager() {
        if (ContextCompat.checkSelfPermission(this ,
                        Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this ,
                            Manifest.permission.ACCESS_FINE_LOCATION)) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
            } else {
                ActivityCompat.requestPermissions(this ,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION) ,
                        FINE_LOCATION_REQUEST_CODE)
            }
        } else {
            locationManager.requestLocationUpdates(locationProvider ,
                    1000 ,
                    1f ,
                    locationListener)
        }
    }

    private fun setupCurrentPeer() {
        doAsync {
            val id = ipfs.id()
            uiThread {
                if (id.containsKey("Addresses")) {
                    val list = id["Addresses"] as List<String>
                    val peer = PeerDTO(username , device , os , list)
                    resourceSender = ResourceSender(ctx , peer , ipfs)
                    if (lastKnownLocation != null) {
                        resourceSender?.send(IPFS_PUB_SUB_CHANNEL , lastKnownLocation !! , null, null)
                    }
                    resourceReceiver = ResourceReceiver(ctx , ipfs)
                    resourceReceiver?.subscribeTo(IPFS_PUB_SUB_CHANNEL , { resource ->
                        adapter.add(resource)
                    } , { ex ->
                        error { ex }
                    })
                } else {
                    error { "Peer doesn't have addresses" }
                }

            }
        }
    }

    private fun setupConfigBtn() {
        configbtn.setOnClickListener {
            PopupMenu(ctx , it).apply {
                menu.apply {
                    addSubMenu(getString(R.string.menu_identity)).apply {
                        add(getString(R.string.menu_identity_peerid)).setOnMenuItemClickListener {
                            val id = ipfsDaemon.config.getAsJsonObject("Identity").getAsJsonPrimitive("PeerID").asString
                            AlertDialog.Builder(ctx).apply {
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
                            val key = ipfsDaemon.config.getAsJsonObject("Identity").getAsJsonPrimitive("PrivKey").asString
                            AlertDialog.Builder(ctx).apply {
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
                        async(50 , { ipfs.swarm.peers() } , {
                            var representation = "No peers."
                            val peers = it as List<Peer>
                            if (peers.isNotEmpty()) {
                                representation = peers.joinToString(separator = "\n" , transform = {
                                    it.address.toString() + it.id
                                })
                            }
                            AlertDialog.Builder(ctx).apply {
                                setTitle(getString(R.string.menu_peers))
                                setMessage(representation)
                                setNeutralButton(getString(R.string.close)) { _ , _ -> }
                            }.show()
                            info { "Swarm Peers success." }
                        } , {
                            info { "Swarm Peers error." }
                        }
                        );true
                    }

                    add(getString(R.string.menu_others)).setOnMenuItemClickListener {
                        async(60 , { ipfs.version() } ,
                                {
                                    val addresses = ipfsDaemon.config.getAsJsonObject("Addresses")
                                    AlertDialog.Builder(ctx).apply {
                                        setTitle(getString(R.string.title_others))
                                        setMessage("""
                                            ${getString(R.string.others_goipfs_version)}: $it
                                            ${getString(R.string.others_api_address)}: ${addresses.getAsJsonPrimitive("API").asString}
                                            ${getString(R.string.others_gateway_address)}: ${addresses.getAsJsonPrimitive("Gateway").asString}
                                        """.trimIndent())
                                        setNeutralButton(getString(R.string.close)) { _ , _ -> }
                                    }.show()
                                } ,
                                {
                                    val addresses = ipfsDaemon.config.getAsJsonObject("Addresses")
                                    AlertDialog.Builder(ctx).apply {
                                        setTitle(getString(R.string.title_others))
                                        setMessage("""
                                            ${getString(R.string.others_goipfs_version)}: ${getString(R.string.others_goipfs_version_unknown)}
                                            ${getString(R.string.others_api_address)}: ${addresses.getAsJsonPrimitive("API").asString}
                                            ${getString(R.string.others_gateway_address)}: ${addresses.getAsJsonPrimitive("Gateway").asString}
                                        """.trimIndent())
                                    }.show()
                                }
                        ); true
                    }
                    addSubMenu(getString(R.string.menu_bootstrap)).apply {
                        add(getString(R.string.menu_bootstrap_list_all)).setOnMenuItemClickListener {
                            async(60 , { ipfs.bootstrap.list() } , {
                                var representation = "No bootstrap nodes."
                                val multiAddresses = it as List<MultiAddress>
                                if (multiAddresses.isNotEmpty()) {
                                    representation = multiAddresses.joinToString(separator = "\n")
                                }
                                AlertDialog.Builder(ctx).apply {
                                    setTitle(getString(R.string.menu_bootstrap_list_all))
                                    setMessage(representation)
                                    setNeutralButton(getString(R.string.close)) { _ , _ -> }
                                }.show()
                                info { "Bootstrap list success." }
                            } , {
                                info { "Bootstrap list error." }
                            }); true
                        }
                        add(getString(R.string.menu_bootstrap_add_node)).setOnMenuItemClickListener {
                            AlertDialog.Builder(ctx).apply {
                                setTitle(getString(R.string.title_bootstrap_add_node))
                                val txtView = EditText(ctx).apply {
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
                                    async(60 , { ipfs.bootstrap.add(nodeAddress) } ,
                                            {
                                                info { "Bootstrap add node success." }
                                            } , {
                                        info { "Bootstrap add node error." }
                                    })
                                }
                                setNegativeButton(getString(R.string.cancel)) { _ , _ -> }
                            }.show(); true
                        }
                    }
                    addSubMenu(getString(R.string.menu_pubsub)).apply {
                        add(getString(R.string.menu_pubsub_list_rooms)).setOnMenuItemClickListener {
                            async(60 , {
                                ipfs.pubsub.ls()
                            } , {

                                val map = it as Map<String , List<String>>
                                val rooms = map["Strings"]
                                var text = "No rooms."
                                if (rooms != null && rooms.isNotEmpty()) {
                                    text = rooms.joinToString(separator = "\n")
                                }
                                AlertDialog.Builder(ctx).apply {
                                    setTitle(getString(R.string.menu_pubsub_list_rooms))
                                    setMessage(text)
                                    setNeutralButton(getString(R.string.close)) { _ , _ -> }
                                }.show()
                                info { "PubSub list rooms success." }
                            } , {
                                info { "PubSub list rooms error." }
                            }); true
                        }
                        add(getString(R.string.menu_pubsub_join_room)).setOnMenuItemClickListener {
                            AlertDialog.Builder(ctx).apply {
                                setTitle(getString(R.string.menu_pubsub_join_room))
                                val txtView = EditText(ctx).apply {
                                    inputType = InputType.TYPE_CLASS_TEXT
                                    setView(this)
                                }
                                setPositiveButton(getString(R.string.apply)) { _ , _ ->
                                    val room = txtView.text.toString()
                                    if (room.isBlank()) {
                                        return@setPositiveButton
                                    }
                                    async(60 , { ipfs.pubsub.sub(room) } ,
                                            {
                                                info { "PubSub join room succedeed." }
                                            } , {
                                        info { "PubSub join room error." }
                                    })
                                }
                                setNegativeButton(getString(R.string.cancel)) { _ , _ -> }
                            }.show(); true
                        }
                        add(getString(R.string.menu_pubsub_post_to_room)).setOnMenuItemClickListener {
                            AlertDialog.Builder(ctx).apply {
                                val layout = LinearLayout(ctx)
                                layout.orientation = LinearLayout.VERTICAL
                                val topicTextView = EditText(ctx).apply {
                                    inputType = InputType.TYPE_CLASS_TEXT
                                    hint = "Enter room name"
                                }

                                layout.addView(topicTextView)
                                val messageTextView = EditText(ctx).apply {
                                    inputType = InputType.TYPE_CLASS_TEXT
                                    hint = "Enter message"
                                }
                                layout.addView(messageTextView)
                                setView(layout)
                                setPositiveButton(getString(R.string.apply)) { _ , _ ->
                                    val room = topicTextView.text.toString()
                                    val message = messageTextView.text.toString()
                                    if (room.isNotBlank() && message.isNotBlank()) {
                                        async(60 ,
                                                { ipfs.pubsub.pub(room , message) } ,
                                                { info { "PubSub post to room $room succedeed." } } ,
                                                { info { "PubSub post room ${room} failed.\")" } }
                                        )
                                    } else {

                                    }
                                }
                                setNegativeButton(getString(R.string.cancel)) { _ , _ -> }
                            }.show(); true
                        }
                        add("Listen").setOnMenuItemClickListener {
                            doAsync {
                                val sub = ipfs.pubsub.sub("topic")
                                ipfs.pubsub.sub("topic" , Consumer {
                                    print(it)
                                } , Consumer {
                                    print(it)
                                })
                                ipfs.pubsub.pub("topic" , "mobile1")
                                ipfs.pubsub.pub("topic" , "mobile2")
                                val results = sub.limit(2).collect(Collectors.toList())
                                uiThread {
                                    print(results)
                                }
                            }
                            true;
                        }

                    }
                }
            }.show()
        }
    }

    private fun setupActionBtn(notimpl: () -> Boolean) {
        actionbtn.setOnClickListener { btn ->
            PopupMenu(ctx , btn).apply {
                menu.apply {
                    add(getString(R.string.menu_garbage_collect)).setOnMenuItemClickListener {
                        true.also {
                            Thread {
                                ipfs.repo.gc()
                                runOnUiThread {
                                    AlertDialog.Builder(ctx)
                                            .setMessage(getString(R.string.garbage_collected)).show()
                                }
                            }.start()
                        }
                    }

                    add(getString(R.string.menu_pins)).setOnMenuItemClickListener { notimpl() }
                    add(getString(R.string.menu_keys)).setOnMenuItemClickListener { notimpl() }
                    addSubMenu(getString(R.string.menu_swarm)).apply {
                        add(getString(R.string.menu_swarm_connect)).setOnMenuItemClickListener { notimpl() }
                        add(getString(R.string.menu_swarm_disconnect)).setOnMenuItemClickListener { notimpl() }
                    }
                    addSubMenu(getString(R.string.menu_dht)).apply {
                        add(getString(R.string.menu_dht_findpeer)).setOnMenuItemClickListener { notimpl() }
                        add(getString(R.string.menu_dht_findprovs)).setOnMenuItemClickListener { notimpl() }
                        add(getString(R.string.menu_dht_query)).setOnMenuItemClickListener { notimpl() }
                    }
                }
            }.show()
        }
    }

    private fun setupFloatingBtns() {
        addFileBtn.setOnClickListener {
            floatingBtnMenu.close(true)

            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                startActivityForResult(this , READ_DOCUMENT_REQUEST_CODE)
            }
        }
        addPhotoBtn.setOnClickListener {
            floatingBtnMenu.close(true)
            Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
                takePictureIntent.resolveActivity(packageManager)?.also {
                    startActivityForResult(takePictureIntent , IMAGE_CAPTURE_REQUEST_CODE)
                }
            }
        }
        addTextBtn.setOnClickListener {
            floatingBtnMenu.close(true)
            AlertDialog.Builder(ctx).apply {
                setTitle(getString(R.string.title_add_text))
                val txt = EditText(ctx).apply {
                    inputType = InputType.TYPE_CLASS_TEXT
                    setView(this)
                }
                setPositiveButton(getString(R.string.apply)) { _ , _ ->
                    resourceSender?.send(IPFS_PUB_SUB_CHANNEL , txt.text.toString() , null, null)
                }
                setNegativeButton(getString(R.string.cancel)) { _ , _ -> }
            }.show()
        }

    }
}
