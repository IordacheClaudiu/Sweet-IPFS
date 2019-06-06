package adapters.rekognition

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.support.v7.widget.RecyclerView
import android.view.View
import io.ipfs.api.IPFS
import io.ipfs.multihash.Multihash
import kotlinx.android.synthetic.main.recyclerview_image_row.view.*
import models.FileDetectionDTO
import models.IPFSImageDetectionResource
import org.jetbrains.anko.*
import utils.notNull
import java.io.IOException
import java.util.concurrent.Future

class ImageHolder(v: View , val ipfs: IPFS , private val maxImageWidth: Int) : RecyclerView.ViewHolder(v) , View.OnClickListener , AnkoLogger {

    private var view: View = v
    private var imageLoadingFuture: Future<Unit>? = null

    fun bind(imageDetectionResource: IPFSImageDetectionResource) {
        if (imageDetectionResource.size != null) {
            val size = imageDetectionResource.size
            var aspectRatio = maxImageWidth.toFloat() / size.width
            val newHeight = size.height * aspectRatio
            view.image_view.layoutParams.height = newHeight.toInt()
            view.image_view.requestLayout()
        } else {
            view.image_view.layoutParams.height = 150
            view.image_view.requestLayout()
        }
        view.resource_hash.text = imageDetectionResource.file!!.hash
        loadBinary(imageDetectionResource.file !!)
    }


    private fun loadBinary(file: FileDetectionDTO) {
        val ipfsHash = Multihash.fromBase58(file.hash)
        imageLoadingFuture = doAsync {
            try {
                val inputStream = ipfs.catStream(ipfsHash)
                file.mimeType.notNull {
                    info { "Load file: $file" }
                    inputStream.use {
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        val rescaled = Bitmap.createScaledBitmap(bitmap , view.image_view.width , view.image_view.height , false)
                        uiThread {
                            view.image_view.setImageBitmap(rescaled)
                            info { "Finish load file : $file" }
                        }
                    }
                }
            } catch (exception: IOException) {
                error { exception }
            }
        }
    }

    fun viewRecycled() {
        view.image_view.setImageBitmap(null)
        imageLoadingFuture?.let {
            it.cancel(true)
        }
    }

    override fun onClick(v: View?) {}
}
