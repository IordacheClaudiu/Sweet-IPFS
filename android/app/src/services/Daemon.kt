package services

import activities.MainActivity
import activities.TabsActivity
import android.R.drawable.ic_menu_close_clear_cancel
import android.app.*
import android.app.NotificationManager.IMPORTANCE_MIN
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import application.get
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import io.ipfs.api.IPFS
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.ReplaySubject
import models.IIpfsResource
import models.PeerDTO
import org.jetbrains.anko.*
import ro.uaic.info.ipfs.R
import utils.*
import utils.Constants.CHANNEL_ID
import utils.Constants.IPFS_PUB_SUB_CHANNEL
import utils.crypto.Crypto
import java.io.FileReader
import java.util.*
import kotlin.collections.ArrayList


val Context.ipfsDaemon get() = Daemon(this)
val ipfs by lazy { IPFS("/ip4/127.0.0.1/tcp/5001") }

class Daemon(private val ctx: Context) : AnkoLogger {
    private val store by lazy { ctx.getExternalFilesDir(null)["ipfs"] }
    private val bin by lazy { ctx.filesDir["ipfsbin"] }
    private val version by lazy { ctx.getExternalFilesDir(null)["version"] }
    private val swarmFile by lazy { store[swarmKey] }
    private val swarmKey by lazy { "swarm.key" }
    val config by lazy { JsonParser().parse(FileReader(store["config"])).asJsonObject }

    private var onSuccess: (() -> Unit)? = null
    private var onError: ((Throwable) -> Unit)? = null
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName , service: IBinder) {
            val binder = service as ForegroundService.ForegroundBinder
            val mService = binder.getService()
            mService.setupFinished.subscribe({
                when(it) {
                    true -> onSuccess?.invoke()
                }
            },{
                onError?.invoke(it)
            })
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
        }
    }

    fun binaryCopied(): Boolean {
        return bin.exists()
    }

    fun nodeInitialized(): Boolean {
        return store.exists() && swarmFile.exists() && config != null
    }

    fun daemonIsRunning(): Boolean {
        return ServiceUtils.isServiceRunning(ctx , ForegroundService::class.java)
    }

    fun refresh(onSuccess: () -> Unit , onError: (Throwable) -> Unit) {
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

    private fun initIfNeeded(onSuccess: () -> Unit , onError: (Throwable) -> Unit) {
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

    private fun startIfNeeded(onSuccess: () -> Unit , onError: (Throwable) -> Unit) {
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

        val type = when(val abi = Build.SUPPORTED_ABIS[0]) {
            "arm64-v8a" -> "arm64"
            "x86_64" -> "amd64"
            "armeabi", "armeabi-v7a" -> "arm"
            "x86", "386" -> "386"
            else -> throw Exception("${ctx.getString(R.string.daemon_unsupported_arch)}: $abi")
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
            array.add("/ip4/52.27.109.224/tcp/4001/ipfs/Qmdx6y1fSaSwoNiqsdvh72SiUaLEhkQ5UAPhwB4r64pbRf") //USER1
            array.add("/ip4/54.200.38.197/tcp/4001/ipfs/QmVYxFLEwR8soew4MTC3otyDo1aoh9hoLLXX8TH4ghZLjn") //USER2
            array.add("/ip4/34.215.50.255/tcp/4001/ipfs/QmcjS2BMHffgsZkAj4RJPSJ3pVxsGsJaWhwnKXGitueMRM") //USER3
            array.add("/ip4/192.168.1.5/tcp/4001/ipfs/QmeJPrqBr4SFw7Wh63YnZN1m7yu29C6KeRkyoVJbpVjxMC") //Local MacBook
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

    private fun start(onSuccess: () -> Unit , onError: (Throwable) -> Unit) {
        this.onSuccess = onSuccess
        this.onError = onError
        val act = ctx as? Activity ?: return
        Intent(act , ForegroundService::class.java).also { intent ->
            act.startService(intent)
            act.bindService(intent , connection , Context.BIND_AUTO_CREATE)
        }

    }

    fun run(cmd: String) = Runtime.getRuntime().exec(
            "${bin.absolutePath} $cmd" ,
            arrayOf("IPFS_PATH=${store.absolutePath}")
    )
}

class ForegroundService : Service() , AnkoLogger {

    // Binder
    private val binder = ForegroundBinder()

    inner class ForegroundBinder : Binder() {
        fun getService(): ForegroundService = this@ForegroundService
    }

    private var showNotification = false

    // IPFS
    private lateinit var daemon: java.lang.Process
    private lateinit var receiver: ResourceReceiver
    private lateinit var sender: ResourceSender
    private lateinit var peer: PeerDTO
    val setupFinished = PublishSubject.create<Boolean>()
    val resourcesObservable = ReplaySubject.create<List<IIpfsResource>>()
    val loadingPeersObservable= ReplaySubject.create<Boolean>(1)

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
    private val locationProvider: String = LocationManager.GPS_PROVIDER
    private var locationManagerStarted =  false
    private var lastKnownLocation: Location? = null
    private val TWO_MINUTES: Long = 1000 * 60 * 2

    private val locationListener = object : LocationListener {

        override fun onLocationChanged(location: Location) {
            info { location }
            if (isBetterLocation(location, lastKnownLocation)) {
                lastKnownLocation = location
                send(location)
            }
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

    // Asymetric crypto
    private val crypto by lazy {Crypto("IPFS_KEYS") }

    override fun onCreate() = super.onCreate().also {
        setupForegroundNotification()
        setupNotificationChannel()
        setupIPFSClient()
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
        if (locationManagerStarted) { return }
        try {
            locationManager.requestLocationUpdates(locationProvider, 2000 , 3f , locationListener)
            locationManagerStarted = true
            lastKnownLocation = locationManager.getLastKnownLocation(locationProvider)
        } catch (e: SecurityException) {
            error { e }
        }
    }

    private fun stopTracking() {
        try {
            locationManager.removeUpdates(locationListener)
            locationManagerStarted = false
        } catch (ex: Exception) {
            error { ex }
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

    private fun showNotification(resource: IIpfsResource) {
        val intent = Intent(this , TabsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this , 0 , intent , 0)

        var builder = NotificationCompat.Builder(this , CHANNEL_ID)
                .setSmallIcon(R.drawable.notificon)
                .setContentTitle(resource.peer.username)
                .setContentText(resource.notificationText())
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        with(NotificationManagerCompat.from(this)) {
            notify(Random().nextInt() , builder.build())
        }
    }

    private fun setupNotificationChannel() {
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

    private fun setupIPFSClient() {
        doAsync {
            while (true.also { Thread.sleep(1000) }) try {
                ipfs.id()
                break
            } catch (ex: Exception) {
                error { ex }
            }
            val id = ipfs.id()
            uiThread {
                if (id.containsKey("Addresses")) {
                    val list = id["Addresses"] as List<String>
                    peer = PeerDTO(username , device , os , list, crypto.publicKey)
                    sender = ResourceSender(this@ForegroundService , peer , ipfs)
                } else {
                    setupFinished.onError(IPFSInvalidNode())
                    error { "Peer doesn't have addresses" }
                }
                receiver = ResourceReceiver(ipfs)
                receiver.subscribeTo(IPFS_PUB_SUB_CHANNEL , {
                    if (it.peer != peer) {
                        resourcesObservable.onNext(listOf(it))
                    }
                    if (showNotification) {
                        showNotification(it)
                    }
                } , {
                    error { it }
                })
                setupFinished.onNext(true)
                fetchLocalResources()
            }
        }
    }

    private fun fetchLocalResources() {
        val parser = ResourceParser()
        loadingPeersObservable.onNext(true)
        doAsync {
                val localHashes = ipfs.refs.local()
                var resources: MutableList<IIpfsResource> = ArrayList()
                localHashes.forEach {
                    try {
                        val bytes = ipfs.cat(it)
                        val json = String(bytes)
                        val resource = parser.parseResource(json)
                        resource?.let { resources.add(resource) }
                        debug { json }
                    } catch (ex: Throwable) {
                        error { ex }
                    }
                }
                uiThread {
                    resourcesObservable.onNext(resources)
                    loadingPeersObservable.onNext(false)
                }
            }
    }

    private fun setupForegroundNotification() {
        val exit = Intent(this , ForegroundService::class.java).apply {
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
    }

    private fun isBetterLocation(location: Location, currentBestLocation: Location?): Boolean {
        if (currentBestLocation == null) {
            // A new location is always better than no location
            return true
        }

        // Check whether the new location fix is newer or older
        val timeDelta: Long = location.time - currentBestLocation.time
        val isSignificantlyNewer: Boolean = timeDelta > TWO_MINUTES
        val isSignificantlyOlder:Boolean = timeDelta < -TWO_MINUTES

        when {
            // If it's been more than two minutes since the current location, use the new location
            // because the user has likely moved
            isSignificantlyNewer -> return true
            // If the new location is more than two minutes older, it must be worse
            isSignificantlyOlder -> return false
        }

        // Check whether the new location fix is more or less accurate
        val isNewer: Boolean = timeDelta > 0L
        val accuracyDelta: Float = location.accuracy - currentBestLocation.accuracy
        val isLessAccurate: Boolean = accuracyDelta > 0f
        val isMoreAccurate: Boolean = accuracyDelta < 0f
        val isSignificantlyLessAccurate: Boolean = accuracyDelta > 200f

        // Check if the old and new location are from the same provider
        val isFromSameProvider: Boolean = location.provider == currentBestLocation.provider

        // Determine location quality using a combination of timeliness and accuracy
        return when {
            isMoreAccurate -> true
            isNewer && !isLessAccurate -> true
            isNewer && !isSignificantlyLessAccurate && isFromSameProvider -> true
            else -> false
        }
    }
}
