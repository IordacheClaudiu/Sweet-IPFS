package activities

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import application.ipfs
import fragments.FeedFragment
import fragments.PeersFragment
import kotlinx.android.synthetic.main.activity_tabbar.*
import models.PeerDTO
import org.jetbrains.anko.*
import ro.uaic.info.ipfs.R
import services.ipfsDaemon
import utils.Constants
import utils.ResourceReceiver
import utils.ResourceSender


class TabsAdapter(fm: FragmentManager) : FragmentPagerAdapter(fm) {

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
}

class TabsActivity : AppCompatActivity(), AnkoLogger, FeedFragment.FeedFragmentListener {

    private val ctx = this as Context

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
                resourceSender?.send(Constants.IPFS_PUB_SUB_CHANNEL , lastKnownLocation !! , null, null)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tabbar)
        val fragmentManager = TabsAdapter(supportFragmentManager)
        viewpager_main.adapter = fragmentManager
        tabs_main.setupWithViewPager(viewpager_main)
    }

    override fun onResume() {
        super.onResume()
        setupLocationManager()
        setupCurrentPeer()
    }

    override fun onDestroy() {
        super.onDestroy()
        resourceReceiver?.unsubscribeFrom(Constants.IPFS_PUB_SUB_CHANNEL)
        locationManager.removeUpdates(locationListener)
    }

    override fun onActivityResult(req: Int , res: Int , rdata: Intent?) {
        super.onActivityResult(req , res , rdata)
        if (res != RESULT_OK) return
        when (req) {
            IMAGE_CAPTURE_REQUEST_CODE -> {
                rdata?.extras?.also {
                    info { "Image taken" }
                    val imageBitmap = it.get("data") as Bitmap
                    resourceSender?.send(Constants.IPFS_PUB_SUB_CHANNEL , imageBitmap , null , null)
                }
            }
            READ_DOCUMENT_REQUEST_CODE -> {
                rdata?.data?.also { uri ->
                    info { "Uri: $uri" }
                    resourceSender?.send(Constants.IPFS_PUB_SUB_CHANNEL , uri , null, null)
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

    override fun feedFragmentOnAddFilePressed(fragment: FeedFragment) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun feedFragmentOnAddImagePressed(fragment: FeedFragment) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun feedFragmentOnAddTextPressed(fragment: FeedFragment) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
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
                        resourceSender?.send(Constants.IPFS_PUB_SUB_CHANNEL , lastKnownLocation !! , null, null)
                    }
                    resourceReceiver = ResourceReceiver(ctx , ipfs)
                    resourceReceiver?.subscribeTo(Constants.IPFS_PUB_SUB_CHANNEL , { resource ->
                        //TODO: send to feed fragment
//                        adapter.add(resource)

                    } , { ex ->
                        error { ex }
                    })
                } else {
                    error { "Peer doesn't have addresses" }
                }

            }
        }
    }
}
