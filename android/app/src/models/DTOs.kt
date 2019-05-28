package models

import android.location.Location
import android.util.Size
import com.google.gson.annotations.SerializedName
import io.ipfs.multihash.Multihash
import java.io.Serializable
import java.util.*

enum class IpfsResourceType {
    LOCATION , TEXT , IMAGE , VIDEO
}

interface IIpfsResource {
    val id: UUID
    val type: IpfsResourceType
    val peer: PeerDTO
    val timestamp: Date
    fun notificationText(): String
}

data class PeerDTO(val username: String ,
                   val device: String ,
                   val os: String ,
                   val addresses: List<String>,
                   val publicKey: String?) : Serializable {

    override fun equals(other: Any?): Boolean {
        if (other !is PeerDTO) return false
        return username == other.username &&
                device == other.username &&
                os == other.os &&
                addresses.containsAll(other.addresses)
    }
}

data class FileDTO(val filename: String , val mimeType: String? , val hash: String) {

    override fun equals(other: Any?): Boolean {
        if (other !is FileDTO) return false
        return this.filename == other.filename &&
                this.mimeType == other.mimeType &&
                this.hash == other.hash
    }
}

data class IpfsTextResource(override val id: UUID ,
                            override val peer: PeerDTO ,
                            override val timestamp: Date ,
                            val text: String) : IIpfsResource {

    override val type = IpfsResourceType.TEXT

    override fun notificationText(): String {
        return "New text posted"
    }

    override fun equals(other: Any?): Boolean {
        if (other !is IpfsTextResource) return false
        return this.id == other.id &&
                this.peer == other.peer &&
                this.timestamp == other.timestamp &&
                this.text == other.text
    }

}

data class IpfsLocationResource(override val id: UUID ,
                                override val peer: PeerDTO ,
                                override val timestamp: Date ,
                                val location: Location) : IIpfsResource {
    override val type = IpfsResourceType.LOCATION

    override fun notificationText(): String {
        return "New location posted."
    }

    override fun equals(other: Any?): Boolean {
        if (other !is IpfsLocationResource) return false
        return this.id == other.id &&
                this.peer == other.peer &&
                this.timestamp == other.timestamp &&
                this.location == other.location
    }

}

data class IpfsImageResource(override val id: UUID ,
                             override val peer: PeerDTO ,
                             override val timestamp: Date ,
                             val file: FileDTO ,
                             val size: Size?) : IIpfsResource {
    override val type = IpfsResourceType.IMAGE

    override fun notificationText(): String {
        return "New image posted."
    }

    override fun equals(other: Any?): Boolean {
        if (other !is IpfsImageResource) return false
        return this.id == other.id &&
                this.peer == other.peer &&
                this.timestamp == other.timestamp &&
                this.file == other.file
    }
}

data class IpfsVideoResource(override val id: UUID ,
                             override val peer: PeerDTO ,
                             override val timestamp: Date ,
                             val file: FileDTO) : IIpfsResource {
    override val type = IpfsResourceType.VIDEO

    override fun notificationText(): String {
        return "New video posted."
    }

    override fun equals(other: Any?): Boolean {
        if (other !is IpfsVideoResource) return false
        return this.id == other.id &&
                this.peer == other.peer &&
                this.timestamp == other.timestamp &&
                this.file == other.file
    }
}

data class RepositoryDTO(val peer: PeerDTO) {

    var multiHashes: MutableSet<Multihash> = mutableSetOf()

    fun add(multihash: Multihash) {
        multiHashes.add(multihash)
    }
}

class SecureEntry {

    @SerializedName("image_analysis_cid")
    var imageAnalysisCID: String? = null

    @SerializedName("image_hash")
    var imageCID: String? = null

    @SerializedName("key_rsa")
    var rsaKey: String? = null

}