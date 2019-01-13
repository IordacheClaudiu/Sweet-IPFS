package models;

import android.location.Location
import io.ipfs.multiaddr.MultiAddress
import io.ipfs.multihash.Multihash

interface IIpfsResource {
    val username: String
    val peer: PeerDTO
    val device: String
    val os: String
}

data class PeerDTO(val multiAddress: MultiAddress)

data class IpfsResource(override val username: String ,
                        override val device: String ,
                        override val peer: PeerDTO ,
                        override val os: String) : IIpfsResource

data class IpfsLocationResource(override val username: String ,
                                override val device: String ,
                                override val peer: PeerDTO ,
                                override val os: String ,
                                val location: Location) : IIpfsResource

data class IpfsDataResource(override val username: String ,
                            override val device: String ,
                            override val peer: PeerDTO ,
                            override val os: String ,
                            val filename: String ,
                            val file: MultiAddress) : IIpfsResource
