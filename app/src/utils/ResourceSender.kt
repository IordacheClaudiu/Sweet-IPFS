package utils

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.location.Location
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.google.gson.GsonBuilder
import com.google.gson.JsonIOException
import io.ipfs.api.IPFS
import io.ipfs.api.NamedStreamable
import io.ipfs.multihash.Multihash
import models.*
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.error
import org.jetbrains.anko.uiThread
import utils.date.DateUtils
import java.io.*
import java.util.*

class ResourceSender(val context: Context , val peer: PeerDTO , val ipfs: IPFS) : AnkoLogger {

    private val gson by lazy {
        val builder = GsonBuilder()
        builder.registerTypeAdapter(Date::class.java , DateDeserializer())
        builder.registerTypeAdapter(Date::class.java , DateSerializer())
        builder.create()
    }

    fun send(channel: String , text: String , onSuccess: ((Multihash) -> Unit)? , onError: ((Exception) -> Unit)?) {
        val textResource = IpfsTextResource(UUID.randomUUID() , peer , DateUtils.GMT.time() , text)
        val json = gson.toJson(textResource)
        val jsonTempFile = json.tempFile
        jsonTempFile.notNull({
            sendJsonFile(channel , it , onSuccess , onError)
        } , {
            val fileCreationException = FileCreationException("File from text could not be created.")
            error { fileCreationException }
            onError?.invoke(fileCreationException)
        })

    }

    fun send(channel: String , location: Location , onSuccess: ((Multihash) -> Unit)? , onError: ((Exception) -> Unit)?) {
        val locationResource = IpfsLocationResource(UUID.randomUUID() , peer , DateUtils.GMT.time() , location)
        val json = gson.toJson(locationResource)
        val jsonTempFile = json.tempFile
        jsonTempFile.notNull({
            sendJsonFile(channel , it , onSuccess , onError)
        } , {
            val fileCreationException = FileCreationException("File from $location  could not be created.")
            error { fileCreationException }
            onError?.invoke(fileCreationException)
        })
    }

    fun send(channel: String , uri: Uri , onSuccess: ((Multihash) -> Unit)? , onError: ((Exception) -> Unit)?) {
        val uriTempFile = uri.tempFile
        uriTempFile.notNull({
            storeAndSendResourceFile(channel , it , onSuccess , onError)
        } , {
            val fileCreationException = FileCreationException("File from ${uri.path} could not be created.")
            error { "Cannot store and send from $uri" }
            onError?.invoke(fileCreationException)
        })
    }

    fun send(channel: String , bitmap: Bitmap , onSuccess: ((Multihash) -> Unit)? , onError: ((Exception) -> Unit)?) {
        val bitmapTempFile = bitmap.tempFile
        bitmapTempFile.notNull({
            storeAndSendResourceFile(channel , it , onSuccess , onError)
        } , {
            val fileCreationException = FileCreationException("Bitmap could not be created.")
            error { fileCreationException }
            onError?.invoke(fileCreationException)
        })

    }

    private fun storeAndSendResourceFile(channel: String , file: File , onSuccess: ((Multihash) -> Unit)? , onError: ((Exception) -> Unit)?) {
        addFile(file) {
            val fileDTO = FileDTO(file.name , Uri.fromFile(file).mimeType , it.toString())
            val mimeType = fileDTO.mimeType
            mimeType.notNull({
                var resource: IIpfsResource? = null
                when {
                    it.startsWith("video") -> {
                        resource = IpfsVideoResource(UUID.randomUUID() , peer , DateUtils.GMT.time() , fileDTO)
                    }
                    it.startsWith("image") -> {
                        resource = IpfsImageResource(UUID.randomUUID() , peer , DateUtils.GMT.time() , fileDTO)
                    }
                }
                resource.notNull {
                    val json = gson.toJson(it)
                    val jsonTempFile = json.tempFile
                    jsonTempFile.notNull({
                        sendJsonFile(channel , it , onSuccess , onError)
                    } , {
                        val fileCreationException = FileCreationException("${file.name} could not be created.")
                        error { fileCreationException }
                        onError?.invoke(fileCreationException)
                    })
                }
            } , {
                val invalidMimeTypeException = InvalidMimeTypeException()
                error { invalidMimeTypeException }
                onError?.invoke(invalidMimeTypeException)
            })
        }
    }

    private fun sendJsonFile(channel: String , file: File , onSuccess: ((Multihash) -> Unit)? , onError: ((Exception) -> Unit)?) {
        addFile(file) { multihash ->
            updateIPNS(multihash)
            doAsync {
                try {
                    ipfs.pubsub.sub(channel)
                    ipfs.pubsub.pub(channel , multihash.toString())
                    uiThread {
                        onSuccess?.invoke(multihash)
                    }
                } catch (exception: Exception) {
                    onError?.invoke(exception)
                }
            }
        }
    }

    private fun addFile(file: File , onSuccess: ((Multihash) -> Unit)) {
        val wrapper = NamedStreamable.FileWrapper(file)
        doAsync {
            val i = ipfs.add(wrapper , false)
            ipfs.pin.add(i.last().hash)
            onSuccess(i.last().hash)
        }
    }

    private fun updateIPNS(multiHash: Multihash) {
        repositoryDTO.add(multiHash)
        repositoryFile.bufferedWriter().use { out ->
            try {
                gson.toJson(repositoryDTO , out)
                addFile(repositoryFile) {
                    doAsync {
                        val ipnsHash = ipfs.name.publish(it)
                        print(ipnsHash)
                    }
                }
            } catch (e: JsonIOException) {
                e.printStackTrace()
            }
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
            val file = File(context.cacheDir , "temp.jpg")
            val outStream = FileOutputStream(file)
            compress(Bitmap.CompressFormat.JPEG , 75 , outStream)
            outStream.close()
            return file
        }

    // IPNS file
    private val repositoryFile: File by lazy {
        val file = File( context.filesDir,peer.username + "_repo.json")
        if (!file.exists()) {
            file.createNewFile()
        }
        return@lazy file
    }
    // IPNS repoDTO
    private val repositoryDTO: RepositoryDTO by lazy {
        repositoryFile.bufferedReader().use { input ->
            val repoDTO = gson.fromJson(input, RepositoryDTO::class.java)
            return@use repoDTO ?: RepositoryDTO(peer)
        }
    }

    // Retrieve uri data
    private val Uri.inputStream get() = context.contentResolver.openInputStream(this)

    // Retrieve uri mime type
    private val Uri.mimeType: String?
        get() {
            //Check uri format to avoid null
            if (scheme != ContentResolver.SCHEME_CONTENT) {
                val fileExtension = MimeTypeMap.getFileExtensionFromUrl(Uri.fromFile(File(path)).toString())
                return MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension)
            }
            val mime = MimeTypeMap.getSingleton()
            return mime.getExtensionFromMimeType(context.contentResolver.getType(this))
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