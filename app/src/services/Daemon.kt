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
import application.get
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import io.ipfs.api.IPFS
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.debug
import org.jetbrains.anko.error
import org.jetbrains.anko.info
import ro.uaic.info.ipfs.R
import utils.IPFSBinaryException
import java.io.FileReader


val Context.ipfsDaemon get() = Daemon(this)
val ipfs by lazy { IPFS("/ip4/127.0.0.1/tcp/5001") }

class Daemon(private val ctx: Context): AnkoLogger {
    private val store by lazy { ctx.getExternalFilesDir(null)["ipfs"] }
    private val bin by lazy { ctx.filesDir["ipfsbin"] }
    private val version by lazy { ctx.getExternalFilesDir(null)["version"] }
    private val swarmFile by lazy { store[swarmKey] }
    private val swarmKey by lazy { "swarm.key" }

    val config by lazy { JsonParser().parse(FileReader(store["config"])).asJsonObject }

    fun binaryCopied(): Boolean {
        return bin.exists()
    }

    fun nodeInitialized(): Boolean {
        return store.exists() && swarmFile.exists() && config != null
    }

    fun daemonIsRunning(): Boolean {
        return ServiceUtils.isServiceRunning(ctx , DaemonService::class.java)
    }

    fun refresh(onSuccess: () -> Unit, onError:(Exception) -> Unit) {
        if (! binaryCopied()) {
            install(callback = {
                info { "Install started." }
                initIfNeeded(onSuccess, onError)
            } , err = {
                error(it)
                onError.invoke(IPFSBinaryException(it))

            })
        } else {
            info { "Install ignored." }
            initIfNeeded(onSuccess, onError)
        }
    }

    private fun initIfNeeded(onSuccess: () -> Unit , onError:(Exception) -> Unit){
        if (! nodeInitialized()) {
            info { "Init started." }
            init(callback = {
                info { "Init finished." }
                startIfNeeded(onSuccess, onError)
            })
        } else {
            info { "Init ignored." }
            startIfNeeded(onSuccess, onError)
        }
    }

    private fun startIfNeeded(onSuccess: () -> Unit , onError:(Exception) -> Unit) {
        if (!daemonIsRunning()) {
            start({
                info { "Daemon started." }
                onSuccess.invoke()
            }, {
                onError.invoke(it)
            })

        } else {
            info { "Daemon ignored." }
            onSuccess.invoke()
        }
    }

    private fun install(callback: () -> Unit , err: (String) -> Unit = {}) {
        val act = ctx as? Activity ?: return
        info { Build.SUPPORTED_ABIS.joinToString { "," } }
        val x86Arch  = Build.SUPPORTED_ABIS.indexOfFirst {
            it.startsWith("x86")
        } != -1
        val armArch =  Build.SUPPORTED_ABIS.indexOfFirst {
            it.startsWith("arm")
        } != -1

        val type = when {
            x86Arch -> "x86"
            armArch -> "arm"
            else -> return err("${ctx.getString(R.string.daemon_unsupported_arch)}: ${Build.SUPPORTED_ABIS}")
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
        callback()
    }

    private fun init(callback: () -> Unit = {}) {
        val act = ctx as? Activity ?: return

        Thread {
            val exec = run("init")
            Thread {
                exec.inputStream.bufferedReader().forEachLine { debug { it } }
            }.start()
            Thread {
                exec.errorStream.bufferedReader().forEachLine { debug { it } }
            }.start()
            exec.waitFor()

            // copy swarm.key
            if (! swarmFile.exists()) {
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
                addProperty("LowWater" , 20)
                remove("HighWater")
                addProperty("HighWater" , 40)
                remove("GracePeriod")
                addProperty("GracePeriod" , "120s")
            }

            config.remove("Bootstrap")
            val array = JsonArray(3)
            array.add("/ip4/35.160.115.103/tcp/4001/ipfs/Qmdx6y1fSaSwoNiqsdvh72SiUaLEhkQ5UAPhwB4r64pbRf")
            array.add("/ip4/54.189.160.162/tcp/4001/ipfs/QmbYQZteYjKLsfEJhg5tnTcTdAKAeutU7ABBsNX3miu5g2")
            array.add("/ip4/54.214.110.255/tcp/4001/ipfs/QmZjm3bQrFgcGJ8o9rzkEEe5pmF7xG3KBc86WprnXXwYgz")
            config.add("Bootstrap" , array)

            GsonBuilder()
                    .setPrettyPrinting()
                    .create()
                    .toJson(config)
                    .toByteArray()
                    .also { store["config"].writeBytes(it) }
            act.runOnUiThread(callback)
        }.start()

    }

    private fun start(onSuccess: () -> Unit , onError: (Exception) -> Unit) {
        val act = ctx as? Activity ?: return
        act.startService(Intent(act , DaemonService::class.java))
        Thread {
            while (true.also { Thread.sleep(1000) }) try {
                version.writeText(
                        ipfs.version() ?: continue
                ); break
            } catch (ex: Exception) {
                error { ex }
            }
            act.runOnUiThread {
                onSuccess.invoke()
            }
        }.start()
    }

    fun run(cmd: String) = Runtime.getRuntime().exec(
            "${bin.absolutePath} $cmd" ,
            arrayOf("IPFS_PATH=${store.absolutePath}")
    )
}

class DaemonService : Service() {

    private val LOGTAG = DaemonService::class.java.name

    override fun onBind(i: Intent) = null

    private lateinit var daemon: Process

    override fun onCreate() = super.onCreate().also { _ ->

        val exit = Intent(this , DaemonService::class.java).apply {
            action = "STOP"
        }.let { PendingIntent.getService(this , 0 , it , 0) }

        val open = Intent(this , MainActivity::class.java)
                .let { PendingIntent.getActivity(this , 0 , it , 0) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            NotificationChannel("sweetipfs" , "Sweet IPFS" , IMPORTANCE_MIN).apply {
                description = "Sweet IPFS"
                getSystemService(NotificationManager::class.java)
                        .createNotificationChannel(this)
            }

        NotificationCompat.Builder(this , "sweetipfs").run {
            setOngoing(true)
            color = Color.parseColor("#4b9fa2")
            setSmallIcon(R.drawable.notificon)
            setShowWhen(false)
            setContentTitle(getString(R.string.notif_title))
            setContentText(getString(R.string.notif_msg))
            setContentIntent(open)
            addAction(ic_menu_close_clear_cancel , getString(R.string.stop) , exit)
            build()
        }.also { startForeground(1 , it) }

        daemon = ipfsDaemon.run("daemon --enable-pubsub-experiment")

        Thread {
            daemon.inputStream.bufferedReader().forEachLine { Log.d(LOGTAG , it) }
        }.start()
        Thread {
            daemon.errorStream.bufferedReader().forEachLine { Log.d(LOGTAG , it) }
        }.start()

    }

    override fun onDestroy() = super.onDestroy().also {
        daemon.destroy()
        NotificationManagerCompat.from(this).cancel(1)
    }

    override fun onStartCommand(i: Intent? , f: Int , id: Int) = START_STICKY.also {
        super.onStartCommand(i , f , id)
        i?.action?.takeIf { it == "STOP" }?.also { stopSelf() }
    }
}