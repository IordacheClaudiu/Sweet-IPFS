package adapters.resources

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.ipfs.api.IPFS
import models.*
import ro.uaic.info.ipfs.R
import utils.notNull

class ResourcesRecyclerAdapter(private val ipfs: IPFS , private val resources: MutableList<IIpfsResource>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup , viewType: Int): RecyclerView.ViewHolder {
        when (viewType) {
            IpfsResourceType.LOCATION.ordinal -> {
                val inflatedView = LayoutInflater.from(parent.context)
                        .inflate(R.layout.reciclerview_location_row , parent , false) as View
                return LocationResourceHolder(inflatedView)
            }
            IpfsResourceType.IMAGE.ordinal -> {
                val inflatedView = LayoutInflater.from(parent.context)
                        .inflate(R.layout.reciclerview_binary_row , parent , false) as View
                return ImageResourceHolder(inflatedView , ipfs)
            }
            IpfsResourceType.VIDEO.ordinal -> {
                val inflatedView = LayoutInflater.from(parent.context)
                        .inflate(R.layout.reciclerview_video_row , parent , false) as View
                return VideoResourceHolder(inflatedView , ipfs)
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
                (holder as TextResourceHolder).bind(resource as IpfsTextResource)
            }
            IpfsResourceType.LOCATION -> {
                (holder as LocationResourceHolder).bind(resource as IpfsLocationResource)
            }
            IpfsResourceType.IMAGE -> {
                (holder as ImageResourceHolder).bind(resource as IpfsImageResource)
            }
            IpfsResourceType.VIDEO -> {
                (holder as VideoResourceHolder).bind(resource as IpfsVideoResource)
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        when (holder) {
            is ResourceHolder<*> -> {
                holder.notNull {
                    it.reset()
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
        resources.sortByDescending {
            it.timestamp.time
        }
        notifyDataSetChanged()
    }


}
