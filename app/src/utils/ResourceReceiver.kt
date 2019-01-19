package utils

import android.content.Context
import com.google.gson.Gson
import io.ipfs.api.IPFS
import io.ipfs.multihash.Multihash
import models.IIpfsResource
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.error
import org.jetbrains.anko.uiThread
import java.io.IOException
import java.util.stream.Stream


class ResourceReceiver(val context: Context , val ipfs: IPFS): AnkoLogger {

    private var subscribedChannels: MutableMap<String, Stream<*>> = mutableMapOf()

    private val gson by lazy {
        Gson()
    }

    fun subscribeTo(channel: String , onSuccess: (IIpfsResource) -> Unit , onError: (IOException) -> Unit) {
        if (subscribedChannels.contains(channel)) {
            return
        }
        doAsync {
            try {
                val stream = ipfs.pubsub.sub(channel)
                uiThread { subscribedChannels.putIfAbsent(channel, stream) }
                stream.forEach {
                    val dataRaw = it[Constants.IPFS_PUB_SUB_DATA] as? String
                    val data = dataRaw?.decode()
                    if (data != null) {
                        try {
                            val multiHash = Multihash.fromBase58(data)
                            val bytes = ipfs.cat(multiHash)
                            val json = String(bytes)
                            print(json)

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