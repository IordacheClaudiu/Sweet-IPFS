package services

import activities.MainActivity
import activities.TabsActivity
import android.R.drawable.ic_menu_close_clear_cancel
import android.app.*
import android.app.NotificationManager.IMPORTANCE_MIN
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.*
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import application.get
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import io.ipfs.api.IPFS
import models.IIpfsResource
import models.PeerDTO
import org.jetbrains.anko.*
import ro.uaic.info.ipfs.R
import utils.*
import utils.Constants.CHANNEL_ID
import utils.Constants.IPFS_PUB_SUB_CHANNEL
import java.io.FileReader
import java.util.*


val Context.ipfsDaemon get() = Daemon(this)
val ipfs by lazy { IPFS("/ip4/127.0.0.1/tcp/5001") }

class Daemon(private val ctx: Context) : AnkoLogger {
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

    fun refresh(onSuccess: () -> Unit , onError: (Exception) -> Unit) {
        if (! binaryCopied()) {
            install(callback = {
                info { "Install started." }
                initIfNeeded(onSuccess , onError)
            } , err = {
                error(it)
                onError.invoke(IPFSBinaryException(it))

            })
        } else {
            info { "Install ignored." }
            initIfNeeded(onSuccess , onError)
        }
    }

    private fun initIfNeeded(onSuccess: () -> Unit , onError: (Exception) -> Unit) {
        if (! nodeInitialized()) {
            info { "Init started." }
            init(callback = {
                info { "Init finished." }
                startIfNeeded(onSuccess , onError)
            })
        } else {
            info { "Init ignored." }
            startIfNeeded(onSuccess , onError)
        }
    }

    private fun startIfNeeded(onSuccess: () -> Unit , onError: (Exception) -> Unit) {
        if (! daemonIsRunning()) {
            start({
                info { "Daemon started." }
                onSuccess.invoke()
            } , {
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
        val x86Arch = Build.SUPPORTED_ABIS.indexOfFirst {
            it.startsWith("x86")
        } != - 1
        val armArch = Build.SUPPORTED_ABIS.indexOfFirst {
            it.startsWith("arm")
        } != - 1

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
            array.add("/ip4/54.212.29.204/tcp/4001/ipfs/Qmdx6y1fSaSwoNiqsdvh72SiUaLEhkQ5UAPhwB4r64pbRf")
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

class DaemonService : Service() , AnkoLogger {

    // Binder
    private val binder = DaemonBinder()

    inner class DaemonBinder : Binder() {
        fun getService(): DaemonService = this@DaemonService
    }

    private var showNotification = false

    // IPFS
    private lateinit var daemon: java.lang.Process
    private lateinit var receiver: ResourceReceiver
    private lateinit var sender: ResourceSender
    private val username by lazy {
        defaultSharedPreferences.getString(Constants.SHARED_PREF_USERNAME , null)
    }
    private val device by lazy {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        listOf(manufacturer , model).joinToString(" ")
    }
    private val os by lazy {
        val version = Build.VERSION.SDK_INT
        val versionRelease = Build.VERSION.RELEASE
        listOf(version , versionRelease).joinToString(" - ")
    }

    // Location
    private val locationManager by lazy { getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    private var lastKnownLocation: Location? = null
    private val locationListener = object : LocationListener {

        override fun onLocationChanged(location: Location) {
            info { location }
            lastKnownLocation = location
            send(location)
        }

        override fun onStatusChanged(provider: String , status: Int , extras: Bundle) {
            info { "onStatusChanged: $status" }
        }

        override fun onProviderEnabled(provider: String) {
            info { "onProviderEnabled: $provider" }
        }

        override fun onProviderDisabled(provider: String) {
            info { "onProviderDisabled: $provider" }
        }
    }


    override fun onCreate() = super.onCreate().also {
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
            daemon.inputStream.bufferedReader().forEachLine { debug { it } }
        }.start()
        Thread {
            daemon.errorStream.bufferedReader().forEachLine { debug { it } }
        }.start()

        createNotificationChannel()

        //TODO: move initialization
        Handler().postDelayed({
            doAsync {
                val id = ipfs.id()
                uiThread {
                    if (id.containsKey("Addresses")) {
                        val list = id["Addresses"] as List<String>
                        val peer = PeerDTO(username , device , os , list)
                        sender = ResourceSender(this@DaemonService , peer , ipfs)
                    } else {
                        error { "Peer doesn't have addresses" }
                    }
                }
            }

            receiver = ResourceReceiver(ipfs)
            receiver.subscribeTo(IPFS_PUB_SUB_CHANNEL , {
                if (showNotification) {
                    showNotification(it)
                } else {
                    EventBus.post(it)
                }
            } , {
                error { it }
            })
        } , 10000)


    }

    override fun onDestroy() = super.onDestroy().also {
        daemon.destroy()
        stopTracking()
        NotificationManagerCompat.from(this).cancel(1)
    }

    override fun onStartCommand(i: Intent? , f: Int , id: Int) = START_STICKY.also {
        super.onStartCommand(i , f , id)
        i?.action?.takeIf { it == "STOP" }?.also { stopSelf() }
    }

    override fun onBind(intent: Intent?): IBinder {
        info { "onBind" }
        showNotification = false
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        info { "onUnbind" }
        showNotification = true
        return true
    }

    override fun onRebind(intent: Intent?) = super.onRebind(intent).also {
        info { "onRebind" }
        showNotification = false
    }

    fun startTracking() {
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER , 2000 , 3f , locationListener)
        } catch (e: SecurityException) {
            error { e }
        }
    }

    fun send(resource: Any) {
        when (resource) {
            is Bitmap -> sender?.send(IPFS_PUB_SUB_CHANNEL , resource , null , null)
            is String -> sender?.send(IPFS_PUB_SUB_CHANNEL , resource , null , null)
            is Location -> sender?.send(IPFS_PUB_SUB_CHANNEL , resource , null , null)
            is Uri -> sender?.send(IPFS_PUB_SUB_CHANNEL , resource , null , null)
        }
    }

    private fun stopTracking() {
        try {
            locationManager.removeUpdates(locationListener)
        } catch (ex: Exception) {
            error { ex }
        }
    }


    private fun showNotification(resource: IIpfsResource) {

        val intent = Intent(this , TabsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this , 0 , intent , 0)

        var builder = NotificationCompat.Builder(this , CHANNEL_ID)
                .setSmallIcon(R.drawable.notificon)
                .setContentTitle(resource.peer.username)
                .setStyle(NotificationCompat.BigTextStyle()
                        .bigText(resource.notificationText))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        with(NotificationManagerCompat.from(this)) {
            notify(Random().nextInt() , builder.build())
        }

    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notification_channel_name)
            val descriptionText = getString(R.string.notification_channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID , name , importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                    getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }


}
