package services

import activities.MainActivity
import android.R.drawable.ic_menu_close_clear_cancel
import android.app.*
import android.app.NotificationManager.IMPORTANCE_MIN
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import android.util.Log
import application.*
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import ro.uaic.info.ipfs.R
import java.io.FileReader


val Context.ipfsDaemon get() = Daemon(this)

class Daemon(private val ctx: Context) {
    private val LOG_TAG = Daemon::class.java.name
    private val store by lazy { ctx.getExternalFilesDir(null)["ipfs"] }
    private val bin by lazy { ctx.filesDir["ipfsbin"] }
    private val version by lazy { ctx.getExternalFilesDir(null)["version"] }
    private val swarmFile by lazy { store[swarmKey] }
    private val swarmKey by lazy { "swarm.key" }

    val config by lazy { JsonParser().parse(FileReader(store["config"])).asJsonObject }

    private fun config(consumer: (JsonObject) -> Unit) {
        GsonBuilder().setPrettyPrinting().create().toJson(config).toByteArray()
                .also { store["config"].writeBytes(it) }
    }

    fun binaryCopied(): Boolean {
        return bin.exists()
    }

    fun nodeInitialized(): Boolean {
        return store.exists() && swarmFile.exists() && config != null
    }

    fun daemonIsRunning(): Boolean {
        return ServiceUtils.isServiceRunning(ctx, DaemonService::class.java)
    }

    fun install(callback: () -> Unit, err: (String) -> Unit = {}) {
        val act = ctx as? Activity ?: return

        val type = when {
            Build.CPU_ABI.toLowerCase().startsWith("x86") -> "x86"
            Build.CPU_ABI.toLowerCase().startsWith("arm") -> "arm"
            else -> return err("${ctx.getString(R.string.daemon_unsupported_arch)}: ${Build.CPU_ABI}")
        }

        val progress = ProgressDialog(ctx).apply {
            setMessage(ctx.getString(R.string.daemon_installing))
            setCancelable(false)
            show()
        }
        // install ipfs
        act.assets.open(type).apply {
            bin.outputStream().also {
                try {
                    copyTo(it)
                } finally {
                    it.close()
                    close()
                }
            }
        }

        bin.setExecutable(true)
        version.writeText(act.assets.open("version").reader().readText())

        progress.dismiss()
        callback()
    }

    fun run(cmd: String) = Runtime.getRuntime().exec(
            "${bin.absolutePath} $cmd",
            arrayOf("IPFS_PATH=${store.absolutePath}")
    )

    fun init(callback: () -> Unit = {}) {
        val act = ctx as? Activity ?: return

        val progress = ProgressDialog(ctx).apply {
            setMessage(ctx.getString(R.string.daemon_init))
            setCancelable(false)
            show()
        }

        Thread {
            val exec = run("init")
            Thread {
                exec.inputStream.bufferedReader().forEachLine { Log.d(LOG_TAG, it) }
            }.start()
            Thread {
                exec.errorStream.bufferedReader().forEachLine { Log.d(LOG_TAG, it) }
            }.start()
            exec.waitFor()

            // copy swarm.key
            if (!swarmFile.exists()) {
                swarmFile.createNewFile()
                act.assets.open(swarmKey).apply {
                    swarmFile.outputStream().also {
                        try {
                            copyTo(it)
                        } finally {
                            it.close()
                            close()
                        }
                    }
                }
            }

            // change config file
            config.getAsJsonObject("Swarm").getAsJsonObject("ConnMgr").apply {
                remove("LowWater")
                addProperty("LowWater", 20)
                remove("HighWater")
                addProperty("HighWater", 40)
                remove("GracePeriod")
                addProperty("GracePeriod", "120s")
            }

            config.remove("Bootstrap")
            val array = JsonArray(1)
            array.add("/ip4/192.168.1.2/tcp/4001/ipfs/QmWeGhsC6x3xWz72vsSAWgS4HeJuPMeU5MVr1NrkmSY7a3")
            config.add("Bootstrap", array)

            config { config }

            progress.dismiss()
            act.runOnUiThread(callback)
        }.start()

    }

    fun start(callback: () -> Unit) {
        val act = ctx as? Activity ?: return

        act.startService(Intent(act, DaemonService::class.java))

        val progress = ProgressDialog(act).apply {
            setMessage(ctx.getString(R.string.daemon_starting))
            setCancelable(false)
            show()
        }

        Thread {

            while (true.also { Thread.sleep(1000) }) try {
                version.writeText(
                        ipfs.version() ?: continue
                ); break
            } catch (ex: Exception) {
            }

            act.runOnUiThread {
                progress.dismiss()
                callback()
            }
        }.start()
    }

}

class DaemonService : Service() {

    private val LOGTAG = DaemonService::class.java.name

    override fun onBind(i: Intent) = null

    private lateinit var daemon: Process

    override fun onCreate() = super.onCreate().also { _ ->

        val exit = Intent(this, DaemonService::class.java).apply {
            action = "STOP"
        }.let { PendingIntent.getService(this, 0, it, 0) }

        val open = Intent(this, MainActivity::class.java)
                .let { PendingIntent.getActivity(this, 0, it, 0) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            NotificationChannel("sweetipfs", "Sweet IPFS", IMPORTANCE_MIN).apply {
                description = "Sweet IPFS"
                getSystemService(NotificationManager::class.java)
                        .createNotificationChannel(this)
            }

        NotificationCompat.Builder(this, "sweetipfs").run {
            setOngoing(true)
            color = Color.parseColor("#4b9fa2")
            setSmallIcon(R.drawable.notificon)
            setShowWhen(false)
            setContentTitle(getString(R.string.notif_title))
            setContentText(getString(R.string.notif_msg))
            setContentIntent(open)
            addAction(ic_menu_close_clear_cancel, getString(R.string.stop), exit)
            build()
        }.also { startForeground(1, it) }

        daemon = ipfsDaemon.run("daemon --enable-pubsub-experiment")

        Thread {
            daemon.inputStream.bufferedReader().forEachLine { Log.d(LOGTAG, it) }
        }.start()
        Thread {
            daemon.errorStream.bufferedReader().forEachLine { Log.d(LOGTAG, it) }
        }.start()

    }

    override fun onDestroy() = super.onDestroy().also {
        daemon.destroy()
        NotificationManagerCompat.from(this).cancel(1)
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int) = START_STICKY.also {
        super.onStartCommand(i, f, id)
        i?.action?.takeIf { it == "STOP" }?.also { stopSelf() }
    }
}