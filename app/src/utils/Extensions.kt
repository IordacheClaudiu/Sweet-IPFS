package utils

import android.graphics.Bitmap
import android.location.Location
import android.util.Base64
import android.util.Size
import com.google.android.gms.maps.model.LatLng
import io.ipfs.multihash.Multihash
import java.io.File
import android.R.attr.path
import android.graphics.BitmapFactory



// String
fun String.decode(): String {
    return Base64.decode(this , Base64.DEFAULT).toString(charset("UTF-8"))
}

fun String.encode(): String {
    return Base64.encodeToString(this.toByteArray(charset("UTF-8")) , Base64.DEFAULT)
}

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

// Extension function to resize bitmap using new width value by keeping aspect ratio

fun Bitmap.resizeByWidth(width: Int): Bitmap {
    val ratio: Float = this.width.toFloat() / this.height.toFloat()
    val height: Int = Math.round(width / ratio)
    return Bitmap.createScaledBitmap(this , width , height , false)
}


// Extension function to resize bitmap using new height value by keeping aspect ratio

fun Bitmap.resizeByHeight(height: Int): Bitmap {
    val ratio: Float = this.height.toFloat() / this.width.toFloat()
    val width: Int = Math.round(height / ratio)
    return Bitmap.createScaledBitmap(this , width , height , false)
}