package adapters;

import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.ipfs.api.Peer
import kotlinx.android.synthetic.main.reciclerview_peer_row.view.*
import models.IIpfsResource
import models.IpfsResourceType
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import ro.uaic.info.ipfs.R

class ResourcesRecyclerAdapter(private val resources: MutableList<IIpfsResource>) : RecyclerView.Adapter<ResourcesRecyclerAdapter.ResourcesHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup , viewType: Int): ResourcesRecyclerAdapter.ResourcesHolder {
        val inflatedView = LayoutInflater.from(parent.context)
                .inflate(R.layout.reciclerview_peer_row , parent , false) as View
        return ResourcesHolder(inflatedView)
    }

    override fun getItemCount(): Int {
        return resources.count()
    }

    override fun onBindViewHolder(holder: ResourcesRecyclerAdapter.ResourcesHolder , position: Int) {
        val resource = resources[position]
        holder.bindResource(resource)
    }

    fun add(newResource: IIpfsResource) {
        add(listOf(newResource))
    }

    fun add(newResources: List<IIpfsResource>) {
        resources.addAll(newResources)
        notifyItemInserted(resources.count() - 1)
    }


    class ResourcesHolder(v: View) : RecyclerView.ViewHolder(v) , View.OnClickListener , AnkoLogger {

        private var view: View = v
        private var resource: IIpfsResource? = null

        init {
            v.setOnClickListener(this)
        }

        override fun onClick(v: View) {
            info { "CLICK!" }
        }

        fun bindResource(resource: IIpfsResource) {
            this.resource = resource
            when (resource.type) {
                IpfsResourceType.LOCATION -> {
                    view.itemPeer.text = "Location:"
                }
                IpfsResourceType.BINARY -> {
                    view.itemPeer.text = "Binary:"
                }
                IpfsResourceType.TEXT -> {
                    view.itemPeer.text = "Text:"
                }
            }
        }

    }
}




