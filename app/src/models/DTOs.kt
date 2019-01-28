package models;

import android.location.Location
import java.io.Serializable
import java.util.*

enum class IpfsResourceType {
    LOCATION , TEXT , IMAGE, VIDEO
}

interface IIpfsResource {
    val id: UUID
    val type: IpfsResourceType
    val peer: PeerDTO
    val timestamp: Date
}

data class PeerDTO(val username: String , val device: String , val os: String , val addresses: List<String>) : Serializable

data class FileDTO(val filename: String , val mimeType: String? , val hash: String)

data class IpfsTextResource(override val id: UUID ,
                            override val peer: PeerDTO ,
                            override val timestamp: Date ,
                            val text: String) : IIpfsResource {
    override val type = IpfsResourceType.TEXT
}

data class IpfsLocationResource(override val id: UUID ,
                                override val peer: PeerDTO ,
                                override val timestamp: Date ,
                                val location: Location) : IIpfsResource {
    override val type = IpfsResourceType.LOCATION
}

data class IpfsImageResource(override val id: UUID ,
                             override val peer: PeerDTO ,
                             override val timestamp: Date ,
                             val file: FileDTO) : IIpfsResource {
    override val type = IpfsResourceType.IMAGE
}

data class IpfsVideoResource(override val id: UUID ,
                             override val peer: PeerDTO ,
                             override val timestamp: Date ,
                             val file: FileDTO) : IIpfsResource {
    override val type = IpfsResourceType.VIDEO
}
