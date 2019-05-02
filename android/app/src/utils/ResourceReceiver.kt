package utils

import io.ipfs.api.IPFS
import io.ipfs.multihash.Multihash
import models.IIpfsResource
import org.jetbrains.anko.*
import java.io.IOException
import java.util.concurrent.Future

class ResourceReceiver(val ipfs: IPFS) : AnkoLogger {

    private val parser = ResourceParser()

    fun subscribeTo(channel: String , onSuccess: (IIpfsResource) -> Unit , onError: (IOException) -> Unit): Future<Unit> {
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
}