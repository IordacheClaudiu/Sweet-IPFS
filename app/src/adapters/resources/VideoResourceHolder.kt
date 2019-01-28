package adapters.resources

import android.net.Uri
import android.view.View
import android.widget.MediaController
import io.ipfs.api.IPFS
import io.ipfs.multihash.Multihash
import kotlinx.android.synthetic.main.reciclerview_video_row.view.*
import models.FileDTO
import models.IpfsVideoResource
import org.jetbrains.anko.info
import utils.date.TimeAgo
import utils.public


class VideoResourceHolder(v: View , private val ipfs: IPFS) : ResourceHolder<IpfsVideoResource>(v) {

    private var view: View = v
    private var mediaController: MediaController
    override lateinit var resource: IpfsVideoResource

    init {
        v.setOnClickListener(this)
        mediaController = MediaController(view.context)
        mediaController.setAnchorView(view.video_view)
        view.video_view.setMediaController(mediaController)
    }

    override fun onClick(v: View) {
        info { "CLICK!" }
    }

    override fun bind(resource: IpfsVideoResource) {
        super.bind(resource)
        view.peer_name.text = resource.peer.username
        view.peer_system.text = resource.peer.os + " " + resource.peer.device
        refreshTimeAgo()
        loadBinary(resource.file)
    }

    override fun reset() {
        if (view.video_view.isPlaying) {
            view.video_view.stopPlayback()
        }
    }

    override fun refreshTimeAgo() {
        view.timestamp.text = TimeAgo.getTimeAgo(resource.timestamp)
    }

    private fun loadBinary(file: FileDTO) {
        val ipfsHash = Multihash.fromBase58(file.hash)
        val url = ipfsHash.public()
        view.video_view.setVideoURI(Uri.parse(url))
        view.video_view.start()
        view.video_view.setOnPreparedListener {
            print("prepared")
        }
    }
}
