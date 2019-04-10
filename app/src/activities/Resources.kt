package activities

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import ro.uaic.info.ipfs.R
import utils.Constants
import java.lang.IllegalStateException

class ResourcesActivity: AppCompatActivity(), AnkoLogger {

    override fun onCreate(state: Bundle?) = super.onCreate(state).also {
        val userHash = intent.getStringExtra(Constants.INTENT_USER_HASH) ?: throw IllegalStateException()
        info { userHash }
        setContentView(R.layout.activity_resources)
    }

}