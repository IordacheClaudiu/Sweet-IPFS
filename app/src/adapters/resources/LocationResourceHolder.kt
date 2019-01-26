package adapters.resources

import android.support.v7.widget.RecyclerView
import android.view.View
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.android.synthetic.main.reciclerview_location_row.view.*
import models.IpfsLocationResource
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import utils.date.TimeAgo
import utils.latLng
import utils.notNull

class LocationResourceHolder(v: View) : RecyclerView.ViewHolder(v) , OnMapReadyCallback , View.OnClickListener , AnkoLogger {

    private var view: View = v
    private lateinit var locationResource: IpfsLocationResource

    var googleMap: GoogleMap? = null

    init {
        v.setOnClickListener(this)
        v.location_view.onCreate(null)
        v.location_view.getMapAsync(this)
    }

    override fun onClick(v: View) {
        info { "CLICK!" }
    }

    override fun onMapReady(googleMap: GoogleMap?) {
        this.googleMap = googleMap
        this.googleMap.notNull {
            MapsInitializer.initialize(view.context)
            it.uiSettings?.isMapToolbarEnabled = false
            locationResource.location.notNull { updateMapContents() }
        }
    }

    fun bindResource(resource: IpfsLocationResource) {
        this.locationResource = resource
        view.peer_name.text = locationResource.peer.username
        view.peer_system.text = locationResource.peer.os + " " + locationResource.peer.device
        view.timestamp.text = TimeAgo.getTimeAgo(locationResource.timestamp)
        if (googleMap != null) {
            updateMapContents()
        }
    }

    fun resetMap() {
        googleMap.notNull {
            it.clear()
            it.mapType = GoogleMap.MAP_TYPE_NONE
        }
    }

    private fun updateMapContents() {
        googleMap.notNull {
            it.clear()
            it.addMarker(MarkerOptions().position(locationResource.location.latLng()))
            val cameraUpdate = CameraUpdateFactory.newLatLngZoom(locationResource.location.latLng() , 10f)
            it.moveCamera(cameraUpdate)
            it.mapType = GoogleMap.MAP_TYPE_NORMAL
        }
    }



}