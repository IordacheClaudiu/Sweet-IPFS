package models;

import android.location.Location
import java.util.*

enum class IpfsResourceType {
    LOCATION, BINARY
}

interface IIpfsResource {
    val id: UUID
    val type: IpfsResourceType
    val peer: PeerDTO
    val timestamp: Date
}

data class PeerDTO(val username: String , val device: String , val os: String , val addresses: List<String>)

data class FileDTO(val filename: String , val mimeType: String? , val hash: String)

data class IpfsLocationResource(override val id: UUID,
                                override val peer: PeerDTO ,
                                override val timestamp: Date ,
                                val location: Location) : IIpfsResource {
   override val type = IpfsResourceType.LOCATION
}

data class IpfsDataResource(override val id: UUID,
                            override val peer: PeerDTO ,
                            override val timestamp: Date ,
                            val file: FileDTO) : IIpfsResource {
    override  val type = IpfsResourceType.BINARY
}
