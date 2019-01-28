package utils

import android.location.Location
import android.util.Base64
import com.google.android.gms.maps.model.LatLng
import io.ipfs.multihash.Multihash

// String
fun String.decode(): String {
    return Base64.decode(this , Base64.DEFAULT).toString(charset("UTF-8"))
}

fun String.encode(): String {
    return Base64.encodeToString(this.toByteArray(charset("UTF-8")) , Base64.DEFAULT)
}

fun <T : Any> T?.notNull(f: (it: T) -> Unit, otherwise: (() -> Unit)? = null) {
    if (this != null) f(this) else otherwise
}

fun <T : Any> T?.notNull(f: (it: T) -> Unit) {
    if (this != null) f(this)
}


// Location
fun Location.latLng(): LatLng {
    return LatLng(latitude, longitude)
}


// Multihash

fun Multihash.public(): String {
    return "https://ipfs.io/ipfs/${this}"
}