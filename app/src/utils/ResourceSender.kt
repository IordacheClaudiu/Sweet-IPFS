package utils

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.location.Location
import android.net.Uri
import io.ipfs.api.IPFS
import android.os.Build
import android.provider.OpenableColumns
import com.google.gson.Gson
import io.ipfs.api.MerkleNode
import io.ipfs.api.NamedStreamable
import io.ipfs.multihash.Multihash
import models.FileDTO
import models.IpfsDataResource
import models.IpfsLocationResource
import models.PeerDTO
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import android.webkit.MimeTypeMap

class ResourceSender(val context: Context , val peer: PeerDTO , val ipfs: IPFS) {

    private val gson by lazy {
        Gson()
    }

    fun send(channel: String , location: Location , callback: ((Multihash) -> Unit)?) {
        val locationResource = IpfsLocationResource(peer , location)
        val json = gson.toJson(locationResource)
        val jsonTempFile = json.tempFile
        if (jsonTempFile != null) {
            sendJsonFile(channel , jsonTempFile , callback)
        }
    }

    fun send(channel: String , uri: Uri , callback: ((Multihash) -> Unit)?) {
        val uriTempFile = uri.tempFile
        if (uriTempFile != null) {
            storeAndSendResourceFile(channel , uriTempFile , callback)
        }
    }

    fun send(channel: String , bitmap: Bitmap , callback: ((Multihash) -> Unit)?) {
        val bitmapTempFile = bitmap.tempFile
        if (bitmapTempFile != null) {
            storeAndSendResourceFile(channel , bitmapTempFile , callback)
        }
    }

    private fun storeAndSendResourceFile(channel: String , file: File , callback: ((Multihash) -> Unit)?) {
        addFile(file) {
            val fileDTO = FileDTO(file.name , Uri.fromFile(file).mimeType , it.toString())
            val dataResource = IpfsDataResource(peer , fileDTO)
            val json = gson.toJson(dataResource)
            val jsonTempFile = json.tempFile
            if (jsonTempFile != null) {
                sendJsonFile(channel , jsonTempFile , callback)
            }
        }
    }

    private fun sendJsonFile(channel: String , file: File , callback: ((Multihash) -> Unit)?) {
        addFile(file) { multihash ->
            doAsync {
                ipfs.pubsub.sub(channel)
                ipfs.pubsub.pub(channel , multihash.toString())
                uiThread {
                    callback?.invoke(multihash)
                }
            }
        }
    }

    private fun addFile(file: File , callback: ((Multihash) -> Unit)) {
        val wrapper = NamedStreamable.FileWrapper(file)
        doAsync {
            var i: List<MerkleNode>? = null
            while (i == null) {
                //TODO: pin content
                i = ipfs.add(wrapper , false)
            }
            callback(i.last().hash)
        }
    }

    // Create temp file from uri
    private val Uri.tempFile: File?
        get() =
            inputStream.copy(File.createTempFile("temp" , name , context.cacheDir))

    // Create temp file from text
    private val String.tempFile: File?
        get() =
            byteInputStream().copy(File.createTempFile("temp" , ".txt" , context.cacheDir))

    // Create temp file from bitmap
    private val Bitmap.tempFile: File?
        get() {
            val file = File(context.cacheDir , "temp")
            val outStream = FileOutputStream(file)
            compress(Bitmap.CompressFormat.JPEG , 75 , outStream)
            outStream.close()
            return file
        }

    // Retrieve uri data
    private val Uri.inputStream get() = context.contentResolver.openInputStream(this)

    // Retrieve uri mime type
    private val Uri.mimeType: String?
        get() {
            //Check uri format to avoid null
            if (scheme != ContentResolver.SCHEME_CONTENT) {
                return MimeTypeMap.getFileExtensionFromUrl(Uri.fromFile(File(path)).toString());
            }
            val mime = MimeTypeMap.getSingleton();
            return mime.getExtensionFromMimeType(context.contentResolver.getType(this));
        }

    // Retrieve uri name
    private val Uri.name: String
        get() = context.contentResolver.query(this ,
                null ,
                null ,
                null ,
                null).run {
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