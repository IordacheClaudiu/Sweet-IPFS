package utils

import android.content.Context
import android.location.Location
import android.net.Uri
import io.ipfs.api.IPFS
import android.os.Build
import android.provider.OpenableColumns
import com.google.gson.Gson
import io.ipfs.api.MerkleNode
import io.ipfs.api.NamedStreamable
import io.ipfs.multihash.Multihash
import models.IpfsLocationResource
import models.PeerDTO
import org.jetbrains.anko.defaultSharedPreferences
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.io.File
import java.io.InputStream

class ResourceSender(val context: Context, val peer: PeerDTO, val ipfs: IPFS) {

    private val gson by lazy {
        Gson()
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

    fun send(channel: String, location: Location, callback: ((Multihash) -> Unit)?) {
        val locationResource = IpfsLocationResource(username , device , peer , os , location)
        val locationJson = gson.toJson(locationResource)
        val locationTempFile = locationJson.tempFile
        if (locationTempFile != null) sendFile(channel,locationTempFile!!, callback)
    }

    fun send(channel:String, filePath: String, callback: ((Multihash) -> Unit)?) {

    }

    private fun sendFile(channel: String, file: File, callback: ((Multihash) -> Unit)?) {
        val wrapper = NamedStreamable.FileWrapper(file)
        doAsync {
            var i: List<MerkleNode>? = null
            while (i == null) {
                i = ipfs.add(wrapper, false)
            }
            if (i != null) {
                ipfs.pubsub.sub(channel)
                ipfs.pubsub.pub(channel , i!!.last().hash.toString())
                ipfs.pubsub.pub(channel , "\n")
            }
            uiThread {
                callback?.invoke(i!!.last().hash)
            }
        }

    }

    // Create temp file from uri
    private val Uri.tempFile: File?
        get() =
            inputStream.copy(File.createTempFile("temp", name, context.cacheDir))

    // Create temp file from text
    private val String.tempFile: File?
        get() =
            byteInputStream().copy(File.createTempFile("temp", ".txt", context.cacheDir))

    // Retrieve uri data
    private val Uri.inputStream get() = context.contentResolver.openInputStream(this)

    // Retrieve uri name
    private val Uri.name: String
        get() = context.contentResolver.query(this, null, null, null, null).run {
            val index = getColumnIndex(OpenableColumns.DISPLAY_NAME)
            moveToFirst()
            getString(index).also { close() }
        }

    private fun InputStream.copy(file: File) = file.outputStream().let {
        try {
            copyTo(it); file
        } catch (ex: Exception) {
            null
        } finally {
            close(); it.close()
        }
    }


}