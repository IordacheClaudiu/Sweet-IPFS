package ro.uaic.info.ipfs

import android.Manifest.permission.*
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.view.View.VISIBLE
import com.tbruyelle.rxpermissions2.RxPermissions
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.defaultSharedPreferences
import org.ligi.tracedroid.sending.TraceDroidEmailSender
import utils.Constants
import utils.DialogUtils

class MainActivity : AppCompatActivity() {

    private val LOG_TAG = MainActivity::class.java.name

    override fun onCreate(state: Bundle?) = super.onCreate(state).also {
        setContentView(R.layout.activity_main)

        TraceDroidEmailSender.sendStackTraces("ciordache92@gmail.com" , this)

        startbtn.setOnClickListener {
            refresh()
        }

        RxPermissions(this)
                .request(INTERNET , WRITE_EXTERNAL_STORAGE , READ_EXTERNAL_STORAGE)
                .subscribe { granted ->
                    if (granted) {
                        val savedUsername = defaultSharedPreferences.getString(Constants.SHARED_PREF_USERNAME, null)
                        if (savedUsername.isNullOrEmpty()) {
                            listOf(usernameEditTxt).forEach { (it as View).visibility = VISIBLE }
                        }
                        if (! (ipfsDaemon.binaryCopied() && ipfsDaemon.nodeInitialized() && ipfsDaemon.daemonIsRunning())) {
                            listOf(startbtn).forEach { (it as View).visibility = VISIBLE }
                            Log.d(LOG_TAG , "Daemon ndsadsat fully initialized.")
                        }
                    } else {
                        Log.e(LOG_TAG , "IPFS requires INTERNET, WRITE_EXTERNAL_STORAGE and READ_EXTERNAL_STORAGE")
                        error("IPFS requires INTERNET, WRITE_EXTERNAL_STORAGE and READ_EXTERNAL_STORAGE")
                    }
                }
    }

    private fun showConsoleActivity() = Intent(this , ConsoleActivity::class.java).run {
        flags += FLAG_ACTIVITY_NO_ANIMATION
        startActivity(this)
    }

    private fun error(msg: String) = text.apply {
        text = msg
        visibility = VISIBLE
    }

    private fun refresh() {
        if (usernameEditTxt.visibility == VISIBLE) {
            val username = usernameEditTxt.text.toString()
            if (username.isBlank()) {
                DialogUtils.messageAlert(this ,
                        "Invalid username" ,
                        "The username cannot be blank.")
                return
            } else {
                val editor = defaultSharedPreferences.edit()
                editor.putString(Constants.SHARED_PREF_USERNAME , username)
                editor.apply()
            }
        }
        if (! ipfsDaemon.binaryCopied()) {
            ipfsDaemon.install(callback = {
                Log.d(LOG_TAG , "Install started.")
            } , err = {
                error(it)
            })
        } else {
            Log.d(LOG_TAG , "Install ignored.")
        }
        if (! ipfsDaemon.nodeInitialized()) {
            Log.d(LOG_TAG , "Init started.")
            ipfsDaemon.init(callback = {

            })
            Log.d(LOG_TAG , "Init finished.")
        } else {
            Log.d(LOG_TAG , "Init ignored.")
        }
        if (! ipfsDaemon.daemonIsRunning()) {
            ipfsDaemon.start { }
            Log.d(LOG_TAG , "Daemon started.")
        } else {
            Log.d(LOG_TAG , "Daemon ignored.")
        }
        showConsoleActivity()
    }
}