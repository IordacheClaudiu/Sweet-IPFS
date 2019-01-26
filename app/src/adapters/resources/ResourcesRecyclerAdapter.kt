package adapters.resources

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import models.*
import ro.uaic.info.ipfs.R
import utils.notNull

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
                        .inflate(R.layout.reciclerview_binary_row , parent , false) as View
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

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        when (holder) {
            is LocationResourceHolder -> {
                holder.notNull {
                    it.resetMap()
                }
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


}
