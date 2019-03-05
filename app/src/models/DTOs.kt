package models;

import android.location.Location
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
}

data class PeerDTO(val username: String , val device: String , val os: String , val addresses: List<String>) : Serializable {

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
                             val file: FileDTO) : IIpfsResource {
    override val type = IpfsResourceType.IMAGE

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
    override fun equals(other: Any?): Boolean {
        if (other !is IpfsVideoResource) return false
        return this.id == other.id &&
                this.peer == other.peer &&
                this.timestamp == other.timestamp &&
                this.file == other.file
    }
}

data class RepositoryDTO(val peer: PeerDTO) {

    private var multiHashes: MutableSet<Multihash> = mutableSetOf()

    fun add(multihash: Multihash) {
        multiHashes.add(multihash)
    }
}