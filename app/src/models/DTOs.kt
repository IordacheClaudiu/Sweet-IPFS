package models;

import android.location.Location
import io.ipfs.multiaddr.MultiAddress

interface IIpfsResource {
    val peer: PeerDTO
}

data class PeerDTO(val username: String , val device: String , val os: String , val addresses: List<String>)

data class FileDTO(val filename: String , val mimeType: String? , val hash: String)

data class IpfsLocationResource(override val peer: PeerDTO ,
                                val location: Location) : IIpfsResource

data class IpfsDataResource(override val peer: PeerDTO ,
                            val file: FileDTO) : IIpfsResource
