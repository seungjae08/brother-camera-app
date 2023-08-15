package imageanalysis
import android.graphics.Bitmap
import androidx.camera.core.ImageAnalysis
import java.nio.ByteBuffer
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView

typealias LumaListener = (luma: Double) -> Unit


class LuminosityAnalyzer(private val listener: LumaListener) : ImageAnalysis.Analyzer {
    private lateinit var previewView: PreviewView
    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()    // Rewind the buffer to zero
        val data = ByteArray(remaining())
        get(data)   // Copy the buffer into a byte array
        return data // Return the byte array
    }

    override fun analyze(image: ImageProxy) {

        val buffer = image.planes[0].buffer
        val data = buffer.toByteArray()
        val pixels = data.map { it.toInt() and 0xFF }
        val luma = pixels.average()

        listener(luma)
        image.close()
    }
}