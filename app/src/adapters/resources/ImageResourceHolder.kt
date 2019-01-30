package adapters.resources

import android.graphics.BitmapFactory
import android.view.View
import io.ipfs.api.IPFS
import io.ipfs.multihash.Multihash
import kotlinx.android.synthetic.main.reciclerview_binary_row.view.*
import models.FileDTO
import models.IpfsImageResource
import org.jetbrains.anko.*
import utils.date.TimeAgo
import utils.notNull
import java.io.IOException
import java.io.InputStream


class ImageResourceHolder(v: View , private val ipfs: IPFS) : ResourceHolder<IpfsImageResource>(v) {

    private var view: View = v
    private var inputStream: InputStream? = null

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
        view.image_view.visibility = View.VISIBLE
        inputStream.notNull({
            if (it.available() != 0) {
                try {
                    it.close()
                } catch (exception: IOException) {
                    error { "Failed to close inputstream" }
                }
            }
        })
    }

    override fun refreshTimeAgo() {
        view.timestamp.text = TimeAgo.getTimeAgo(resource.timestamp)
    }

    private fun loadBinary(file: FileDTO) {
        val ipfsHash = Multihash.fromBase58(file.hash)
        doAsync {
            try {
                inputStream = ipfs.catStream(ipfsHash)
                file.mimeType.notNull {
                    debug { "Load file: $file" }
                    try {
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        uiThread {
                            view.image_view.setImageBitmap(bitmap)
                        }
                    } finally {
                        inputStream?.close()
                        inputStream = null
                    }
                }
            } catch (exception: IOException) {

            }
        }
    }
}
