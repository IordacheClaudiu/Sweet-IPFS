package activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.google.gson.Gson
import models.IPFSImageDetectionResource
import ro.uaic.info.ipfs.R


fun Context.ImageRekognitionIntent(analysisJSON: String): Intent {
    return Intent(this , ImageRekognitionActivity::class.java).apply {
        putExtra(INTENT_ANALYSIS_JSON , analysisJSON)
    }
}

private const val INTENT_ANALYSIS_JSON = "analysis_json"

class ImageRekognitionActivity : AppCompatActivity() {

    private lateinit var imageDetectionResource: IPFSImageDetectionResource

    override fun onCreate(state: Bundle?) = super.onCreate(state).also {
        val analysisJSON = intent.getStringExtra(INTENT_ANALYSIS_JSON)
                ?: throw IllegalStateException()
        imageDetectionResource = Gson().fromJson<IPFSImageDetectionResource>(analysisJSON ,
                IPFSImageDetectionResource::class.java)
                ?: throw  IllegalStateException()
        setContentView(R.layout.activity_image_analysis)
    }

}