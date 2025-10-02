package com.example.app_rb_aid

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.tasks.await
import com.google.firebase.ml.modeldownloader.CustomModel
import com.google.firebase.ml.modeldownloader.DownloadType
import com.google.firebase.ml.modeldownloader.FirebaseModelDownloader
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.exp

data class DetectionResult(
    val label: String,
    val score: Float
) {
    val isPositive get() = label.equals("RB", ignoreCase = true)
}

class MLDetector private constructor(
    private val interpreter: Interpreter,
    private val inputSize: Int = 224
) {

    companion object {
        private const val ASSET_MODEL_PATH = "models/efficientnet_retino_detection.tflite"

        /** OFFLINE: load model dari assets */
        fun fromAssets(context: Context, threads: Int = 2): MLDetector {
            val opts = Interpreter.Options().apply { numThreads = threads }
            val map = loadModelFileFromAssets(context, ASSET_MODEL_PATH)
            return MLDetector(Interpreter(map, opts))
        }

        /** ONLINE: download model dari Firebase (nama remote model di console) */
        suspend fun fromFirebase(context: Context, remoteModelName: String, threads: Int = 2): MLDetector {
            val file = downloadFirebaseModel(remoteModelName)
            val opts = Interpreter.Options().apply { numThreads = threads }
            val mapped = loadModelFileFromFile(file)
            return MLDetector(Interpreter(mapped, opts))
        }

        private fun loadModelFileFromAssets(context: Context, path: String): MappedByteBuffer {
            val afd = context.assets.openFd(path)
            FileInputStream(afd.fileDescriptor).use { fis ->
                val channel = fis.channel
                return channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
            }
        }

        private fun loadModelFileFromFile(file: File): MappedByteBuffer {
            val fis = FileInputStream(file)
            val channel = fis.channel
            return channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size()).also { fis.close() }
        }

        private suspend fun downloadFirebaseModel(remoteModelName: String): File {
            val customModel: CustomModel = FirebaseModelDownloader.getInstance()
                .getModel(remoteModelName, DownloadType.LATEST_MODEL, /* conditions */ null)
                .await()
            return customModel.file ?: error("File model Firebase tidak ditemukan")
        }
    }

    fun classify(bitmap: Bitmap): DetectionResult {
        val input = preprocess(bitmap, inputSize)
        val output = Array(1) { FloatArray(2) } // [1, numClasses] — sesuaikan jika berbeda
        interpreter.run(input, output)

        val probs = softmax(output[0])
        val (idx, score) = argmax(probs)
        val label = if (idx == 0) "Normal" else "RB" // mapping label — sesuaikan
        return DetectionResult(label, score)
    }

    fun classifyFromUri(context: Context, uri: Uri): DetectionResult {
        val bmp = decodeBitmapFromUri(context, uri)
        return classify(bmp)
    }

    // ========= Utils =========

    private fun preprocess(src: Bitmap, size: Int): ByteBuffer {
        val bmp = centerCropAndResize(src, size, size)
        val input = ByteBuffer.allocateDirect(1 * size * size * 3 * 4).order(ByteOrder.nativeOrder())
        val pixels = IntArray(size * size)
        bmp.getPixels(pixels, 0, size, 0, 0, size, size)
        var i = 0
        while (i < pixels.size) {
            val p = pixels[i]
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            input.putFloat(r)
            input.putFloat(g)
            input.putFloat(b)
            i++
        }
        input.rewind()
        return input
    }

    private fun centerCropAndResize(src: Bitmap, dstW: Int, dstH: Int): Bitmap {
        val w = src.width
        val h = src.height
        val size = minOf(w, h)
        val x = (w - size) / 2
        val y = (h - size) / 2
        val cropped = Bitmap.createBitmap(src, x, y, size, size)
        return Bitmap.createScaledBitmap(cropped, dstW, dstH, true)
    }

    private fun softmax(x: FloatArray): FloatArray {
        val max = x.maxOrNull() ?: 0f
        var sum = 0f
        val out = FloatArray(x.size)
        for (i in x.indices) { out[i] = exp((x[i] - max).toDouble()).toFloat(); sum += out[i] }
        for (i in x.indices) out[i] /= sum
        return out
    }

    private fun argmax(x: FloatArray): Pair<Int, Float> {
        var idx = 0
        var best = x[0]
        for (i in 1 until x.size) if (x[i] > best) { best = x[i]; idx = i }
        return idx to best
    }

    private fun decodeBitmapFromUri(context: Context, uri: Uri): Bitmap {
        return when (uri.scheme?.lowercase()) {
            "content" -> {
                val ins: InputStream = context.contentResolver.openInputStream(uri)!!
                BitmapFactory.decodeStream(ins).also { ins.close() }
            }
            "file", null -> {
                val file = File(uri.path ?: error("Path kosong"))
                BitmapFactory.decodeStream(FileInputStream(file))
            }
            else -> error("Skema URI tidak didukung: ${uri.scheme}")
        }
    }
}
