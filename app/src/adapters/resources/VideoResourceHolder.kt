package adapters.resources

import android.net.Uri
import android.view.View
import io.ipfs.api.IPFS
import io.ipfs.multihash.Multihash
import kotlinx.android.synthetic.main.recyclerview_video_row.view.*
import models.IpfsVideoResource
import org.jetbrains.anko.info
import utils.date.TimeAgo
import utils.public
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.ExoPlayerFactory
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory
import com.google.android.exoplayer2.source.ExtractorMediaSource
import utils.notNull

class VideoResourceHolder(v: View , private val ipfs: IPFS) : ResourceHolder<IpfsVideoResource>(v) {

    private var view: View = v
    override lateinit var resource: IpfsVideoResource
    private var videoPlayer: SimpleExoPlayer? = null

    init {
        v.setOnClickListener(this)
    }

    override fun onClick(v: View) {
        info { "CLICK!" }
    }

    override fun bind(resource: IpfsVideoResource) {
        super.bind(resource)
        refreshTimeAgo()
        view.peer_name.text = resource.peer.username
        view.peer_system.text = resource.peer.os + " " + resource.peer.device



    }

    private fun buildMediaSource(uri: Uri): MediaSource {
        return ExtractorMediaSource.Factory(
                DefaultHttpDataSourceFactory("exoplayer-codelab")).createMediaSource(uri)
    }

    override fun viewAttached() {
        videoPlayer = ExoPlayerFactory.newSimpleInstance(view.context,
                DefaultRenderersFactory(view.context),
                DefaultTrackSelector(),
                DefaultLoadControl())
        view.video_view.player = videoPlayer

        val ipfsHash = Multihash.fromBase58(resource.file.hash)
        val url = ipfsHash.public()
        val uri = Uri.parse(url)
        val mediaSource = buildMediaSource(uri)
        videoPlayer?.prepare(mediaSource , true , false)
    }

    override fun viewDetached() {
        videoPlayer.notNull {
            it.release()
        }
        videoPlayer = null
    }

    override fun refreshTimeAgo() {
        view.timestamp.text = TimeAgo.getTimeAgo(resource.timestamp)
    }

}
