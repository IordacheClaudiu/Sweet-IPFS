package application

import android.app.Application
import android.content.Context
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import net.glxn.qrgen.android.MatrixToImageConfig
import net.glxn.qrgen.android.MatrixToImageWriter
import org.ligi.tracedroid.TraceDroid
import java.io.File

class App : Application() {
    override fun onCreate() = super.onCreate().also { TraceDroid.init(this) }
}

val Context.ctx get() = this

operator fun File.get(path: String) = File(this , path)

// Create qr code
fun qr(text: String , width: Int , height: Int) = QRCodeWriter()
        .encode(text , BarcodeFormat.QR_CODE , width , height , mapOf(EncodeHintType.MARGIN to 0))
        .let { MatrixToImageWriter.toBitmap(it , MatrixToImageConfig(0xFF000000.toInt() , 0x00000000)) }

