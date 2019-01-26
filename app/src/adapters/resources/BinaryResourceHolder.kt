package adapters.resources

import android.support.v7.widget.RecyclerView
import android.view.View
import models.IpfsDataResource
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info


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


