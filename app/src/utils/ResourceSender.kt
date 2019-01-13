package utils

import android.content.Context
import android.location.Location
import io.ipfs.api.IPFS
import android.os.Build
import models.IpfsLocationResource
import models.PeerDTO
import org.jetbrains.anko.defaultSharedPreferences

class ResourceSender(val context: Context, val peer: PeerDTO, val ipfs: IPFS) {
    fun send(location: Location) {
        val locationResource = IpfsLocationResource(username , device , peer , os , location)
        print(peer)
        print(os)
        print(device)
    }

    fun send(filePath: String) {

    }

    private val device by lazy {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        listOf(manufacturer , model).joinToString(",")
    }

    private val os by lazy {
        val version = Build.VERSION.SDK_INT
        val versionRelease = Build.VERSION.RELEASE
        listOf(version , versionRelease).joinToString(",")
    }

    private val username by lazy {
        context.defaultSharedPreferences.getString(Constants.SHARED_PREF_USERNAME , null)
    }
}