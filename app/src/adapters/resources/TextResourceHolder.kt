package adapters.resources

import android.support.v7.widget.RecyclerView
import android.view.View
import kotlinx.android.synthetic.main.reciclerview_text_row.view.*
import models.IpfsTextResource
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import utils.date.TimeAgo

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
        view.timestamp.text = TimeAgo.getTimeAgo(textResource.timestamp)
    }

}