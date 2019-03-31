package adapters.resources

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
import io.ipfs.api.IPFS
import io.ipfs.multihash.Multihash
import kotlinx.android.synthetic.main.recyclerview_binary_row.view.*
import models.FileDTO
import models.IpfsImageResource
import org.jetbrains.anko.*
import utils.date.TimeAgo
import utils.notNull
import utils.resizeByWidth
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.Future
import kotlin.math.max


class ImageResourceHolder(v: View , private val ipfs: IPFS) : ResourceHolder<IpfsImageResource>(v) {

    private var view: View = v
    private var imageLoadingFuture: Future<Unit>? = null

    override lateinit var resource: IpfsImageResource

    init {
        v.setOnClickListener(this)
    }

    override fun onClick(v: View) {
        info { "CLICK!" }
    }

    override fun bind(resource: IpfsImageResource) {
        super.bind(resource)
        view.peer_name.text = resource.peer.username
        view.peer_system.text = resource.peer.os + " " + resource.peer.device
        refreshTimeAgo()
        loadBinary(resource.file)
    }

    override fun viewRecycled() {
        view.image_view.setImageBitmap(null)
        imageLoadingFuture?.let {
            it.cancel(true)
        }
    }

    override fun refreshTimeAgo() {
        view.timestamp.text = TimeAgo.getTimeAgo(resource.timestamp)
    }

    private fun loadBinary(file: FileDTO) {
        val ipfsHash = Multihash.fromBase58(file.hash)
        imageLoadingFuture = doAsync {
            try {
                val inputStream = ipfs.catStream(ipfsHash)
                file.mimeType.notNull {
                    info { "Load file: $file" }
                    try {
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        val rescaled = bitmap.resizeByWidth(view.width)
                        uiThread {
                            view.image_view.setImageBitmap(rescaled)
                            info { "Finish load file : $file" }
                        }
                    } finally {
                        inputStream?.close()
                    }
                }
            } catch (exception: IOException) {
                error { exception }
            }
        }
    }
}
