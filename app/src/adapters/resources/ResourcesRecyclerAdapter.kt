package adapters.resources

import android.support.v7.util.DiffUtil
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.ipfs.api.IPFS
import models.*
import ro.uaic.info.ipfs.R

class ResourcesRecyclerAdapter(private val ipfs: IPFS , private val resources: MutableList<IIpfsResource>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup , viewType: Int): RecyclerView.ViewHolder {
        when (viewType) {
            IpfsResourceType.LOCATION.ordinal -> {
                val inflatedView = LayoutInflater.from(parent.context)
                        .inflate(R.layout.recyclerview_location_row , parent , false) as View
                return LocationResourceHolder(inflatedView)
            }
            IpfsResourceType.IMAGE.ordinal -> {
                val inflatedView = LayoutInflater.from(parent.context)
                        .inflate(R.layout.recyclerview_binary_row , parent , false) as View
                return ImageResourceHolder(inflatedView , ipfs, parent.width)
            }
            IpfsResourceType.VIDEO.ordinal -> {
                val inflatedView = LayoutInflater.from(parent.context)
                        .inflate(R.layout.recyclerview_video_row , parent , false) as View
                return VideoResourceHolder(inflatedView , ipfs)
            }
            else -> {
                val inflatedView = LayoutInflater.from(parent.context)
                        .inflate(R.layout.recyclerview_text_row , parent , false) as View
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
            is ResourceHolder<*> -> { holder.viewRecycled() }
        }
    }

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        when (holder) {
            is ResourceHolder<*> -> { holder.viewAttached() }
        }
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        when (holder) {
            is ResourceHolder<*> -> { holder.viewDetached() }
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
        if (!resources.contains(newResource)) {
            val oldResources = resources.toList()
            resources.add(newResource)
            resources.sortByDescending {
                it.timestamp.time
            }
            val newResources = resources.toList()
            val diffResult = DiffUtil.calculateDiff(ResourcesDiffUtilCallback(newResources, oldResources))
            diffResult.dispatchUpdatesTo(this)
        }
    }
}
