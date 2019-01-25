package adapters

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.reciclerview_text_row.view.*
import models.*
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import ro.uaic.info.ipfs.R

class ResourcesRecyclerAdapter(private val resources: MutableList<IIpfsResource>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup , viewType: Int): RecyclerView.ViewHolder {
        when (viewType) {
            IpfsResourceType.LOCATION.ordinal -> {
                val inflatedView = LayoutInflater.from(parent.context)
                        .inflate(R.layout.reciclerview_location_row , parent , false) as View
                return LocationResourceHolder(inflatedView)
            }
            IpfsResourceType.BINARY.ordinal -> {
                val inflatedView = LayoutInflater.from(parent.context)
                        .inflate(R.layout.reciclerview_location_row , parent , false) as View
                return BinaryResourceHolder(inflatedView)
            }
            else -> {
                val inflatedView = LayoutInflater.from(parent.context)
                        .inflate(R.layout.reciclerview_text_row , parent , false) as View
                return TextResourceHolder(inflatedView)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder , position: Int) {
        val resource = resources[position]
        when (resource.type) {
            IpfsResourceType.TEXT -> {
                (holder as TextResourceHolder).bindResource(resource as IpfsTextResource)
            }
            IpfsResourceType.LOCATION -> {
                (holder as LocationResourceHolder).bindResource(resource as IpfsLocationResource)
            }
            IpfsResourceType.BINARY -> {
                (holder as BinaryResourceHolder).bindResource(resource as IpfsDataResource)
            }
        }
    }

    override fun getItemCount(): Int {
        return resources.size
    }

    override fun getItemViewType(position: Int): Int {
        val resource = resources[position]
        return resource.type.ordinal
    }

    fun add(newResource: IIpfsResource) {
        add(listOf(newResource))
    }

    fun add(newResources: List<IIpfsResource>) {
        resources.addAll(newResources)
        notifyItemInserted(resources.count() - 1)
    }


    class TextResourceHolder(v: View) : RecyclerView.ViewHolder(v) , View.OnClickListener , AnkoLogger {

        private var view: View = v
        private lateinit var textResource: IpfsTextResource

        init {
            v.setOnClickListener(this)
        }

        override fun onClick(v: View) {
            info { "CLICK!" }
        }

        fun bindResource(resource: IpfsTextResource) {
            this.textResource = resource
            view.peer_name.text = textResource.peer.username
            view.peer_system.text = textResource.peer.os + " " + textResource.peer.device
            view.peer_message.text = textResource.text
            view.timestamp.text = "1d ago"
        }

    }

    class LocationResourceHolder(v: View) : RecyclerView.ViewHolder(v) , View.OnClickListener , AnkoLogger {

        private var view: View = v
        private var locationResource: IpfsLocationResource? = null

        init {
            v.setOnClickListener(this)
        }

        override fun onClick(v: View) {
            info { "CLICK!" }
        }

        fun bindResource(resource: IpfsLocationResource) {
            this.locationResource = resource
//            view.type.text = "Location"
        }

    }

    class BinaryResourceHolder(v: View) : RecyclerView.ViewHolder(v) , View.OnClickListener , AnkoLogger {

        private var view: View = v
        private var binaryResource: IpfsDataResource? = null

        init {
            v.setOnClickListener(this)
        }

        override fun onClick(v: View) {
            info { "CLICK!" }
        }

        fun bindResource(resource: IpfsDataResource) {
            this.binaryResource = resource
//            view.type.text = "Binary"
        }

    }
}




