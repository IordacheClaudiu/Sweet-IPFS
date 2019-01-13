package models;

import android.location.Location
import io.ipfs.multiaddr.MultiAddress

interface IIpfsResource {
    val username: String
    val device: String
    val os: String
}

data class IpfsResource(override val username: String ,
                        override val device: String ,
                        override val os: String) : IIpfsResource

data class IpfsLocationResource(override val username: String ,
                                override val device: String ,
                                override val os: String ,
                                val location: Location) : IIpfsResource

data class IpfsDataResource(override val username: String ,
                            override val device: String ,
                            override val os: String ,
                            val filename: String ,
                            val file: MultiAddress) : IIpfsResource
