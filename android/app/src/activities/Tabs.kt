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
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import com.tbruyelle.rxpermissions2.RxPermissions
import fragments.FeedFragment
import fragments.PeersFragment
import io.ipfs.api.Peer
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.android.synthetic.main.activity_tabbar.*
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import ro.uaic.info.ipfs.R
import services.ForegroundService
import utils.Constants.INTENT_USER_HASH


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

                it.resourcesObservable?.observeOn(AndroidSchedulers.mainThread()).subscribe {
                    val position = viewpager_main.currentItem
                    val fragment = tabsAdapter.registeredFragment(position)
                    if (fragment is FeedFragment) {
                        fragment.add(it)
                    }
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
        tabsAdapter = TabsAdapter(supportFragmentManager)
        viewpager_main.adapter = tabsAdapter
        tabs_main.setupWithViewPager(viewpager_main)
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
