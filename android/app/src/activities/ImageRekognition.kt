package activities

import adapters.rekognition.ImageRekognitionAdapter
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import com.google.gson.Gson
import kotlinx.android.synthetic.main.fragment_feed.*
import models.IPFSImageDetectionResource
import ro.uaic.info.ipfs.R
import services.ipfs


fun Context.ImageRekognitionIntent(analysisJSON: String): Intent {
    return Intent(this , ImageRekognitionActivity::class.java).apply {
        putExtra(INTENT_ANALYSIS_JSON , analysisJSON)
    }
}

private const val INTENT_ANALYSIS_JSON = "analysis_json"

class ImageRekognitionActivity : AppCompatActivity() {

    private lateinit var imageDetectionResource: IPFSImageDetectionResource

    // UI
    private lateinit var linearLayoutManager: LinearLayoutManager
    private lateinit var adapter: ImageRekognitionAdapter

    override fun onCreate(state: Bundle?) = super.onCreate(state).also {
        val analysisJSON = intent.getStringExtra(INTENT_ANALYSIS_JSON)
                ?: throw IllegalStateException()
        imageDetectionResource = Gson().fromJson<IPFSImageDetectionResource>(analysisJSON ,
                IPFSImageDetectionResource::class.java)
                ?: throw  IllegalStateException()
        setContentView(R.layout.activity_image_analysis)
        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        linearLayoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = linearLayoutManager
        adapter = ImageRekognitionAdapter(imageDetectionResource , ipfs)
        recyclerView.adapter = adapter
    }
}