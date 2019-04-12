package utils

import android.location.Location
import android.util.Base64
import android.util.Size
import com.google.android.gms.maps.model.LatLng
import io.ipfs.multihash.Multihash
import java.io.File
import android.graphics.BitmapFactory

// String
fun String.decode(): String {
    return Base64.decode(this , Base64.DEFAULT).toString(charset("UTF-8"))
}

// Optionals
fun <T : Any> T?.notNull(f: (it: T) -> Unit , otherwise: (() -> Unit)? = null) {
    if (this != null) f(this) else otherwise
}

fun <T : Any> T?.notNull(f: (it: T) -> Unit) {
    if (this != null) f(this)
}

// Location
fun Location.latLng(): LatLng {
    return LatLng(latitude , longitude)
}

// Multihash
fun Multihash.public(): String {
    return "https://ipfs.io/ipfs/${this}"
}

// File
fun File.imageSize(): Size? {
    val options = BitmapFactory.Options()
    options.inJustDecodeBounds = true
    BitmapFactory.decodeFile(absolutePath, options)
    if (options.outWidth != - 1 && options.outHeight != - 1) {
        return Size(options.outWidth, options.outHeight)
    } else {
        return null
    }
}
