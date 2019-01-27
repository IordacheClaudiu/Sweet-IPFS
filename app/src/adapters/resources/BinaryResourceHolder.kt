package adapters.resources

import android.view.View
import kotlinx.android.synthetic.main.reciclerview_binary_row.view.*
import models.IpfsDataResource
import org.jetbrains.anko.info
import utils.date.TimeAgo


class BinaryResourceHolder(v: View) : ResourceHolder<IpfsDataResource>(v) {

    private var view: View = v
    override lateinit var resource: IpfsDataResource

    init {
        v.setOnClickListener(this)
    }

    override fun onClick(v: View) {
        info { "CLICK!" }
    }

    override fun bind(resource: IpfsDataResource) {
        super.bind(resource)
        view.peer_name.text = resource.peer.username
        view.peer_system.text = resource.peer.os + " " + resource.peer.device
        refreshTimeAgo()
    }

    override fun reset() {

    }

    override fun refreshTimeAgo() {
        view.timestamp.text = TimeAgo.getTimeAgo(resource.timestamp)
    }
}
