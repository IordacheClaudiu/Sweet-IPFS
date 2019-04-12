package utils

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import io.ipfs.api.IPFS
import io.ipfs.multiaddr.MultiAddress
import io.ipfs.multihash.Multihash
import models.*
import org.jetbrains.anko.*
import utils.Constants.IPFS_RESOURCE_TYPE_KEY
import java.io.IOException
import java.util.*
import java.util.stream.Stream


class ResourceReceiver(val context: Context , val ipfs: IPFS) : AnkoLogger {

    private var subscribedChannels: MutableMap<String , Stream<*>> = mutableMapOf()
    private val parser = ResourceParser()

    fun subscribeTo(channel: String , onSuccess: (IIpfsResource) -> Unit , onError: (IOException) -> Unit) {
        if (subscribedChannels.contains(channel)) {
            return
        }
        doAsync {
            try {
                val stream = ipfs.pubsub.sub(channel)
                uiThread { subscribedChannels.putIfAbsent(channel , stream) }
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

    fun unsubscribeFrom(channel: String) {
        if (subscribedChannels.containsKey(channel)) {
            subscribedChannels.remove(channel)
        }
    }
}