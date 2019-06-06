package adapters.rekognition

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.ipfs.api.IPFS
import models.IPFSImageDetectionResource
import ro.uaic.info.ipfs.R

enum class ImageRekognitionSectionType {
    HEADER , CONFIDENCE
}

class ImageRekognitionAdapter(val imageAnalysis: IPFSImageDetectionResource , private val ipfs: IPFS) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup , viewType: Int): RecyclerView.ViewHolder {
        return when (getItemViewType(viewType)) {
            ImageRekognitionSectionType.HEADER.ordinal -> {
                val inflatedView = LayoutInflater.from(parent.context)
                        .inflate(R.layout.recyclerview_image_row , parent , false) as View
                ImageHolder(inflatedView , ipfs , parent.width)
            }
            else -> {
                val inflatedView = LayoutInflater.from(parent.context)
                        .inflate(R.layout.recyclerview_confidence_row , parent , false) as View
                ImageConfidenceHolder(inflatedView)
            }
        }
    }

    override fun getItemCount(): Int {
        if (imageAnalysis.file != null) {
            return imageAnalysis.file !!.detection.labels.count() + 1
        }
        return 0
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is ImageHolder) {
            holder.viewRecycled()
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder , position: Int) {
        when (holder) {
            is ImageConfidenceHolder -> {
                val label = imageAnalysis.file !!.detection.labels[position-1]
                holder.bind(label)
            }
            is ImageHolder -> {
                holder.bind(imageAnalysis)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        if (position == 0) {
            return ImageRekognitionSectionType.HEADER.ordinal
        }
        return ImageRekognitionSectionType.CONFIDENCE.ordinal
    }
}