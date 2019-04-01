package adapters.resources

import android.support.v7.util.DiffUtil
import models.IIpfsResource

class ResourcesDiffUtilCallback(private val newResources: List<IIpfsResource> ,
                                private val oldResources: List<IIpfsResource>) : DiffUtil.Callback() {

    override fun getOldListSize(): Int {
        return oldResources.size
    }

    override fun getNewListSize(): Int {
        return newResources.size
    }

    override fun areContentsTheSame(oldItemPosition: Int , newItemPosition: Int): Boolean {
        return true
    }

    override fun areItemsTheSame(oldItemPosition: Int , newItemPosition: Int): Boolean {
        return oldResources[oldItemPosition].id == newResources[newItemPosition].id
    }
}