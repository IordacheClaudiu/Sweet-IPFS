package adapters.rekognition

import android.support.v7.widget.RecyclerView
import android.view.View
import kotlinx.android.synthetic.main.recyclerview_confidence_row.view.*
import models.LabelDTO
import org.jetbrains.anko.AnkoLogger

class ImageConfidenceHolder(v: View) : RecyclerView.ViewHolder(v) , View.OnClickListener , AnkoLogger {

    private var view: View = v

    fun bind(label: LabelDTO) {
        view.label_name.text = label.name
        val number2digits: Double = Math.round(label.confidence * 100.0) / 100.0
        view.label_confidence.text = "$number2digits%"
    }

    override fun onClick(v: View?) {}
}
