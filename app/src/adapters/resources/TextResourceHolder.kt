package adapters.resources

import android.support.v7.widget.RecyclerView
import android.view.View
import kotlinx.android.synthetic.main.reciclerview_text_row.view.*
import models.IIpfsResource
import models.IpfsTextResource
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import org.jetbrains.anko.runOnUiThread
import utils.date.TimeAgo
import java.util.*
import kotlin.concurrent.fixedRateTimer

abstract class ResourceHolder<T : IIpfsResource>(v: View) : RecyclerView.ViewHolder(v) , View.OnClickListener , AnkoLogger {
    abstract var resource: T
    private lateinit var timer: Timer

    open fun bind(resource: T) {
        this.resource = resource
        timer = fixedRateTimer("timer" ,
                false , Date() ,
                1000) {
            itemView.context.runOnUiThread {
                refreshTimeAgo()
            }
        }
    }

    open fun viewRecycled() {
        timer.cancel()
    }

    open fun viewAttached() {}
    open fun viewDetached() {}

    abstract fun refreshTimeAgo()

}

class TextResourceHolder(v: View) : ResourceHolder<IpfsTextResource>(v) {

    private var view: View = v
    override lateinit var resource: IpfsTextResource

    init {
        v.setOnClickListener(this)
    }

    override fun onClick(v: View) {
        info { "CLICK!" }
    }

    override fun bind(resource: IpfsTextResource) {
        super.bind(resource)
        view.peer_name.text = resource.peer.username
        view.peer_system.text = resource.peer.os + " " + resource.peer.device
        view.peer_message.text = resource.text
        refreshTimeAgo()
    }

    override fun refreshTimeAgo() {
        view.timestamp.text = TimeAgo.getTimeAgo(resource.timestamp)
    }
}