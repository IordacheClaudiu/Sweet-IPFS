package adapters.resources

import android.view.View
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.android.synthetic.main.reciclerview_location_row.view.*
import models.IpfsLocationResource
import org.jetbrains.anko.info
import utils.date.TimeAgo
import utils.latLng
import utils.notNull

class LocationResourceHolder(v: View) : ResourceHolder<IpfsLocationResource>(v) , OnMapReadyCallback {

    private var view: View = v
    override lateinit var resource: IpfsLocationResource
    var googleMap: GoogleMap? = null

    init {
        v.setOnClickListener(this)
        v.image_view.onCreate(null)
        v.image_view.getMapAsync(this)
    }

    override fun onClick(v: View) {
        info { "CLICK!" }
    }

    override fun onMapReady(googleMap: GoogleMap?) {
        this.googleMap = googleMap
        this.googleMap.notNull {
            MapsInitializer.initialize(view.context)
            it.uiSettings?.isMapToolbarEnabled = false
            resource.location.notNull { googleMap.notNull { updateMapContents(it) } }
        }
    }

    override fun bind(resource: IpfsLocationResource) {
        super.bind(resource)
        view.peer_name.text = resource.peer.username
        view.peer_system.text = resource.peer.os + " " + resource.peer.device
        refreshTimeAgo()
        googleMap.notNull { updateMapContents(it) }
    }

    override fun viewRecycled() {
        googleMap.notNull {
            it.clear()
            it.mapType = GoogleMap.MAP_TYPE_NONE
        }
    }

    override fun refreshTimeAgo() {
        view.timestamp.text = TimeAgo.getTimeAgo(resource.timestamp)
    }


    private fun updateMapContents(googleMap: GoogleMap) {
        googleMap.clear()
        googleMap.addMarker(MarkerOptions().position(resource.location.latLng()))
        val cameraUpdate = CameraUpdateFactory.newLatLngZoom(resource.location.latLng() , 20f)
        googleMap.moveCamera(cameraUpdate)
        googleMap.mapType = GoogleMap.MAP_TYPE_NORMAL
    }


}