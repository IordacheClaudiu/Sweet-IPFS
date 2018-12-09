package ro.uaic.info.ipfs

import android.Manifest.permission.*
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.view.View.VISIBLE
import com.tbruyelle.rxpermissions2.RxPermissions
import kotlinx.android.synthetic.main.activity_main.*
import org.ligi.tracedroid.sending.TraceDroidEmailSender

class MainActivity : AppCompatActivity() {

    private val LOG_TAG = MainActivity::class.java.name

    private fun showConsoleActivity() = Intent(this, ConsoleActivity::class.java).run {
        flags += FLAG_ACTIVITY_NO_ANIMATION
        startActivity(this)
    }

    fun show() = listOf(text, startbtn).forEach { (it as View).visibility = VISIBLE }

    private fun error(msg: String) = text.apply {
        text = msg
        visibility = VISIBLE
    }

    var refresh: () -> Unit = {
        if (!ipfsDaemon.binaryCopied()) {
            ipfsDaemon.install(callback = {
                Log.d(LOG_TAG, "Install started.")
            }, err = {
                error(it)
            })
        } else {
            Log.d(LOG_TAG, "Install ignored.")
        }
        if (!ipfsDaemon.nodeInitialized()) {
            Log.d(LOG_TAG, "Init started.")
            ipfsDaemon.init(callback = {

            })
            Log.d(LOG_TAG, "Init finished.")
        } else {
            Log.d(LOG_TAG, "Init ignored.")
        }
        if (!ipfsDaemon.daemonIsRunning()) {
            ipfsDaemon.start { }
            Log.d(LOG_TAG, "Daemon started.")
        } else {
            Log.d(LOG_TAG, "Daemon ignored.")
        }
        showConsoleActivity()
    }

    override fun onResume() = super.onResume().also { refresh() }

    override fun onCreate(state: Bundle?) = super.onCreate(state).also {
        setContentView(R.layout.activity_main)

        TraceDroidEmailSender.sendStackTraces("ciordache92@gmail.com", this)

        startbtn.setOnClickListener {
            refresh()
        }

        RxPermissions(this)
                .request(INTERNET, WRITE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE)
                .subscribe { granted ->
                    if (granted) {
                        if (!(ipfsDaemon.binaryCopied() && ipfsDaemon.nodeInitialized() && ipfsDaemon.daemonIsRunning())) {
                            show()
                            Log.d(LOG_TAG, "Daemon not fully initialized.")
                        }
                    } else {
                        Log.e(LOG_TAG, "IPFS requires INTERNET, WRITE_EXTERNAL_STORAGE and READ_EXTERNAL_STORAGE")
                        error("IPFS requires INTERNET, WRITE_EXTERNAL_STORAGE and READ_EXTERNAL_STORAGE")
                    }
                }
    }

}