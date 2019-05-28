package utils

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import models.*
import java.util.*

class ResourceParser {

    private val gson by lazy {
        val builder = GsonBuilder()
        builder.registerTypeAdapter(Date::class.java , DateDeserializer())
        builder.registerTypeAdapter(Date::class.java , DateSerializer())
        builder.create()
    }
    fun parseResource(string: String): IIpfsResource? {
        val root = JsonParser().parse(string).asJsonObject
        if (root.has(Constants.IPFS_RESOURCE_TYPE_KEY)) {
            val type = root.get(Constants.IPFS_RESOURCE_TYPE_KEY).asString
            when (IpfsResourceType.valueOf(type)) {
                IpfsResourceType.LOCATION -> {
                    return gson.fromJson<IpfsLocationResource>(string , IpfsLocationResource::class.java)
                }
                IpfsResourceType.IMAGE -> {
                   return gson.fromJson<IpfsImageResource>(string , IpfsImageResource::class.java)
                }
                IpfsResourceType.VIDEO -> {
                    return gson.fromJson<IpfsVideoResource>(string , IpfsVideoResource::class.java)
                }
                IpfsResourceType.TEXT -> {
                    return gson.fromJson<IpfsTextResource>(string , IpfsTextResource::class.java)
                }
            }
        }
        return null
    }

    fun parseSecureEntry(string: String): SecureEntry? {
        return gson.fromJson<SecureEntry>(string , SecureEntry::class.java)
    }
}