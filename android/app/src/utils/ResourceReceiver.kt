package utils

import io.ipfs.api.IPFS
import io.ipfs.multihash.Multihash
import models.IIpfsResource
import models.SecureEntry
import org.jetbrains.anko.*
import utils.crypto.Crypto
import java.io.IOException
import java.util.concurrent.Future

class ResourceReceiver(val ipfs: IPFS) : AnkoLogger {

    private val parser = ResourceParser()

    fun subscribeToPublic(channel: String , onSuccess: (IIpfsResource) -> Unit , onError: (IOException) -> Unit): Future<Unit> {
      return doAsync {
            try {
                val stream = ipfs.pubsub.sub(channel)
                stream.forEach {
                    val dataRaw = it[Constants.IPFS_PUB_SUB_DATA] as? String
                    val data = dataRaw?.decode()
                    info { data }
                    data.notNull {
                        try {
                            val multiHash = Multihash.fromBase58(data)
                            val bytes = ipfs.cat(multiHash)
                            val json = String(bytes)
                            val resource = parser.parseResource(json)
                            resource?.let { uiThread { onSuccess(resource) } }
                        } catch (ex: Throwable) {
                            error { ex }
                        }
                    }
                }
            } catch (ex: IOException) {
                uiThread { onError(ex) }
            }
        }
    }


    fun subscribeToPrivate(channel: String , onSuccess: (SecureEntry) -> Unit , onError: (IOException) -> Unit): Future<Unit> {
        return doAsync {
            try {
                val stream = ipfs.pubsub.sub(channel)
                stream.forEach {
                    val dataRaw = it[Constants.IPFS_PUB_SUB_DATA] as? String
                    val data = dataRaw?.decode()
                    info { data }
                    data.notNull {
                        try {
                            val secureEntry = parser.parseSecureEntry(it)
                            secureEntry?.let {
                                val crypto = Crypto("IPFS_KEYS")
                                val aesKey = crypto.decryptRSA(it.aesKeyEncrypted!!)
                                val multiHash = Multihash.fromBase58(it.imageAnalysisCID)
                                val bytes = ipfs.cat(multiHash)
                                val json = crypto.decryptAES(String(bytes), aesKey, it.aesIV!!)
                                info { json }
                                uiThread { onSuccess(secureEntry) }
                            }
                        } catch (ex: Throwable) {
                            error { ex }
                        }
                    }
                }
            } catch (ex: IOException) {
                uiThread { onError(ex) }
            }
        }
    }


}