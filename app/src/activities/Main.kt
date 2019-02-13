package activities

import android.Manifest.permission.*
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.view.View.VISIBLE
import com.tbruyelle.rxpermissions2.RxPermissions
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.*
import org.ligi.tracedroid.sending.TraceDroidEmailSender
import ro.uaic.info.ipfs.R
import services.ipfsDaemon
import utils.Constants
import utils.DialogUtils

class MainActivity : AppCompatActivity() , AnkoLogger {

    override fun onCreate(state: Bundle?) = super.onCreate(state).also {
        setContentView(R.layout.activity_main)

        TraceDroidEmailSender.sendStackTraces("ciordache92@gmail.com" , this)

        startbtn.setOnClickListener {
            if (usernameEditTxt.visibility == VISIBLE) {
                val username = usernameEditTxt.text.toString()
                if (username.isBlank()) {
                    DialogUtils.messageAlert(this ,
                            "Invalid username" ,
                            "The username cannot be blank.")
                    return@setOnClickListener
                } else {
                    val editor = defaultSharedPreferences.edit()
                    editor.putString(Constants.SHARED_PREF_USERNAME , username)
                    editor.apply()
                }
            }
            val dialog = indeterminateProgressDialog(message = "Please wait a bit…", title = "Starting daemon")
            ipfsDaemon.refresh({
                dialog.dismiss()
                showConsoleActivity()
            }, {
                dialog.dismiss()
                alert { it.message }.show()

            })
        }

        RxPermissions(this)
                .request(INTERNET , WRITE_EXTERNAL_STORAGE , READ_EXTERNAL_STORAGE)
                .subscribe { granted ->
                    if (granted) {
                        val savedUsername = defaultSharedPreferences.getString(Constants.SHARED_PREF_USERNAME , null)
                        if (savedUsername.isNullOrEmpty()) {
                            listOf(usernameEditTxt).forEach { (it as View).visibility = VISIBLE }
                        }
                        if (! (ipfsDaemon.binaryCopied() && ipfsDaemon.nodeInitialized() && ipfsDaemon.daemonIsRunning())) {
                            listOf(startbtn).forEach { (it as View).visibility = VISIBLE }
                            info { "Daemon fully initialized." }
                        }
                    } else {
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
}