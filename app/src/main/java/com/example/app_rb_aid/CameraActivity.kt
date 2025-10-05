package com.example.app_rb_aid

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

class CameraActivity : AppCompatActivity() {

    // ---------- UI ----------
    private lateinit var previewView: androidx.camera.view.PreviewView
    private lateinit var instructionText: TextView
    private lateinit var flashOverlay: View
    private lateinit var overlayView: CameraOverlayView

    private lateinit var captureFab: FloatingActionButton
    private lateinit var capturedImageView: ImageView
    private lateinit var reviewActions: LinearLayout
    private lateinit var redoFab: FloatingActionButton
    private lateinit var confirmFab: FloatingActionButton

    // ---------- Camera ----------
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    // ---------- Mode & State ----------
    private var isOffline: Boolean = false
    private var currentEye = "RIGHT"
    private var rightEyeUri: Uri? = null
    private var leftEyeUri: Uri? = null
    private var isReviewMode = false
    private var pendingPhotoUri: Uri? = null
    private var lastPhotoFile: File? = null

    // ---------- Inference ----------
    private val cloudUrl = "https://tscnn-api-468474828586.asia-southeast2.run.app/predict"
    private val http by lazy { OkHttpClient() }

    // PyTorch model lokal (.pt). Taruh di assets/models/…
    private var localModel: Module? = null
    private val inputSize = 64
    private val MEAN = floatArrayOf(2.3147659e-05f, -5.0520233e-05f, 1.3798560e-05f)
    private val STD  = floatArrayOf(0.8244203f, 0.8003612f, 0.7935678f)

    // ---------- Coroutines ----------
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ---------- Permission ----------
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            if (ok) startCamera() else {
                Toast.makeText(this, "Izin kamera ditolak", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        // Mode (dari ModeManager globalmu)
        isOffline = (ModeManager.mode == ModeManager.Mode.OFFLINE)

        // Bind views
        previewView = findViewById(R.id.previewView)
        instructionText = findViewById(R.id.instructionText)
        flashOverlay = findViewById(R.id.flashOverlay)
        overlayView = findViewById(R.id.CameraOverlayView)
        captureFab = findViewById(R.id.captureFab)

        capturedImageView = findViewById(R.id.capturedImageView)
        reviewActions = findViewById(R.id.reviewActions)
        redoFab = findViewById(R.id.redoFab)
        confirmFab = findViewById(R.id.confirmFab)

        cameraExecutor = Executors.newSingleThreadExecutor()
        instructionText.text = "Ambil foto mata kanan"

        // Muat model lokal jika mode offline (atau fallback)
        if (isOffline) {
            try {
                val path = assetFilePath("models/cnn2_precise_best.pt") // ubah jika namanya beda
                localModel = Module.load(path)
                Log.d("CameraActivity", "Model lokal PyTorch dimuat.")
            } catch (e: Exception) {
                Log.e("CameraActivity", "Gagal load model lokal: ${e.message}")
                localModel = null
            }
        }

        // Permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }

        // Actions
        captureFab.setOnClickListener { takePhoto() }
        redoFab.setOnClickListener { exitReview(deleteFile = true) }
        confirmFab.setOnClickListener { confirmPhotoAndProceed() }
    }

    // ---------------- Camera ----------------
    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder().build()
            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
                )
            } catch (e: Exception) {
                Log.e("CameraActivity", "Start camera error: ${e.message}", e)
                Toast.makeText(this, "Gagal memulai kamera", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        if (isReviewMode) return
        // Cara utama: snapshot preview + crop ROI dari overlay
        captureRoiFromPreview()?.let { roiFile ->
            showFlash()
            lastPhotoFile = roiFile
            enterReview(Uri.fromFile(roiFile))
            return
        }

        // Fallback: gunakan ImageCapture bila bitmap preview belum siap
        val imageCapture = imageCapture ?: return
        val dir = externalCacheDir ?: cacheDir
        val photoFile = File(dir, "${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Toast.makeText(this@CameraActivity, "Gagal mengambil foto", Toast.LENGTH_SHORT).show()
                }
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    showFlash()
                    lastPhotoFile = photoFile
                    enterReview(Uri.fromFile(photoFile))
                }
            }
        )
    }

    /** Tangkap bitmap dari PreviewView, crop sesuai kotak overlay, simpan JPG di cache. */
    private fun captureRoiFromPreview(): File? {
        val bmp: Bitmap = previewView.bitmap ?: return null
        val box = overlayView.getBoxRect()

        val L = max(0f, min(box.left,  bmp.width - 1f))
        val T = max(0f, min(box.top,   bmp.height - 1f))
        val R = max(L + 1, min(box.right,  bmp.width.toFloat())).toFloat()
        val B = max(T + 1, min(box.bottom, bmp.height.toFloat())).toFloat()

        val cw = (R - L).toInt()
        val ch = (B - T).toInt()
        if (cw < 4 || ch < 4) return null

        val cropped = Bitmap.createBitmap(bmp, L.toInt(), T.toInt(), cw, ch)
        val dir = externalCacheDir ?: cacheDir
        val out = File(dir, "roi_${System.currentTimeMillis()}.jpg")
        FileOutputStream(out).use { cropped.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        return out
    }

    // ---------------- Review ----------------
    private fun enterReview(uri: Uri) {
        isReviewMode = true
        pendingPhotoUri = uri

        capturedImageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
        capturedImageView.adjustViewBounds = true
        capturedImageView.setImageURI(uri)

        capturedImageView.visibility = View.VISIBLE
        reviewActions.visibility = View.VISIBLE
        captureFab.visibility = View.GONE
        overlayView.visibility = View.GONE

        instructionText.text = if (currentEye == "RIGHT")
            "Periksa foto mata kanan" else "Periksa foto mata kiri"
    }

    private fun exitReview(deleteFile: Boolean) {
        if (deleteFile) try { lastPhotoFile?.delete() } catch (_: Exception) {}
        isReviewMode = false
        pendingPhotoUri = null

        capturedImageView.setImageDrawable(null)
        capturedImageView.visibility = View.GONE
        reviewActions.visibility = View.GONE
        captureFab.visibility = View.VISIBLE
        overlayView.visibility = View.VISIBLE

        instructionText.text = if (currentEye == "RIGHT")
            "Ambil foto mata kanan" else "Ambil foto mata kiri"
    }

    private fun confirmPhotoAndProceed() {
        val uri = pendingPhotoUri ?: return
        if (currentEye == "RIGHT") {
            rightEyeUri = uri
            currentEye = "LEFT"
            exitReview(deleteFile = false)
            instructionText.text = "Ambil foto mata kiri"
        } else {
            leftEyeUri = uri
            onBothPhotosReady()
        }
    }

    // ---------------- Setelah dua foto siap ----------------
    private fun onBothPhotosReady() {
        val r = rightEyeUri ?: return
        val l = leftEyeUri ?: return

        scope.launch {
            val (rawRLabel, rScore) = runInference(r)
            val (rawLLabel, lScore) = runInference(l)

            val rLabel = normalizeLabel(rawRLabel)
            val lLabel = normalizeLabel(rawLLabel)

            val diagnosis = if (isPositive(rLabel) || isPositive(lLabel))
                "Terindikasi retinoblastoma (cek ke dokter)."
            else
                "Tidak terindikasi retinoblastoma."

            if (isOffline) {
                // OFFLINE → langsung HasilActivity (tanpa form)
                val go = Intent(this@CameraActivity, HasilActivity::class.java).apply {
                    putExtra("EXTRA_NAMA", "Mode Offline")
                    putExtra("EXTRA_NIK", "")
                    putExtra("EXTRA_TANGGAL", "")

                    putExtra("RIGHT_EYE_URI", r.toString())
                    putExtra("LEFT_EYE_URI",  l.toString())

                    putExtra("RIGHT_LABEL", rLabel)
                    putExtra("RIGHT_SCORE", rScore)
                    putExtra("LEFT_LABEL",  lLabel)
                    putExtra("LEFT_SCORE",  lScore)

                    putExtra("DIAGNOSIS", diagnosis)
                }
                startActivity(go)
                finish()
            } else {
                // ONLINE → bawa ke DataPasienActivity untuk isi data & simpan Firebase
                val go = Intent(this@CameraActivity, DataPasienActivity::class.java).apply {
                    putExtra("RIGHT_EYE_URI", r.toString())
                    putExtra("LEFT_EYE_URI",  l.toString())

                    putExtra("RIGHT_LABEL", rLabel)
                    putExtra("RIGHT_SCORE", rScore)
                    putExtra("LEFT_LABEL",  lLabel)
                    putExtra("LEFT_SCORE",  lScore)
                }
                startActivity(go)
                finish()
            }
        }
    }

    // ---------------- Inference (online → fallback offline) ----------------
    private suspend fun runInference(uri: Uri): Pair<String, Float> = withContext(Dispatchers.IO) {
        // ONLINE lebih dulu jika mode online
        if (!isOffline) {
            try {
                val f = File(uri.path!!)
                val body = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", f.name, f.asRequestBody("image/jpeg".toMediaType()))
                    .build()
                val req = Request.Builder().url(cloudUrl).post(body).build()
                http.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val json = JSONObject(resp.body!!.string())
                        val label = json.getString("prediction")
                        val conf  = json.optDouble("confidence", 0.0).toFloat()
                        return@withContext label to conf
                    }
                }
                Log.w("CameraActivity", "Cloud response bukan 200 → fallback offline.")
            } catch (e: Exception) {
                Log.w("CameraActivity", "Cloud error: ${e.message} → fallback offline.")
            }
        }

        // OFFLINE (PyTorch)
        localModel?.let { model ->
            try {
                val bmp = BitmapFactory.decodeFile(uri.path!!)
                val scaled = Bitmap.createScaledBitmap(bmp, inputSize, inputSize, true)
                val input = bitmapToNormalizedCHW(scaled, MEAN, STD)
                val output = model.forward(IValue.from(input)).toTensor()
                val out = output.dataAsFloatArray
                if (out.isEmpty()) return@withContext "Unknown" to -1f

                var idx = 0
                var best = out[0]
                for (i in 1 until out.size) if (out[i] > best) { best = out[i]; idx = i }
                val label = if (idx == 0) "Retinoblastoma" else "Normal"
                return@withContext label to best
            } catch (e: Exception) {
                Log.e("OfflineInference", "Error offline: ${e.message}", e)
            }
        }
        // Bila semuanya gagal
        return@withContext "Unknown" to -1f
    }

    // --- Preprocess untuk PyTorch: CHW, normalisasi ---
    private fun bitmapToNormalizedCHW(
        bmp: Bitmap,
        mean: FloatArray,
        std: FloatArray
    ): Tensor {
        val w = bmp.width
        val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)

        val ch = 3
        val arr = FloatArray(1 * ch * h * w)
        var rIdx = 0
        var gIdx = h * w
        var bIdx = 2 * h * w

        for (y in 0 until h) {
            val off = y * w
            for (x in 0 until w) {
                val p = pixels[off + x]
                val r = ((p shr 16) and 0xFF) / 255f
                val g = ((p shr 8) and 0xFF) / 255f
                val b = (p and 0xFF) / 255f

                arr[rIdx++] = (r - mean[0]) / std[0]
                arr[gIdx++] = (g - mean[1]) / std[1]
                arr[bIdx++] = (b - mean[2]) / std[2]
            }
        }
        return Tensor.fromBlob(arr, longArrayOf(1, 3, h.toLong(), w.toLong()))
    }

    // ---------------- Utils & helpers ----------------
    private fun normalizeLabel(raw: String): String = when {
        raw.equals("RB", true) -> "RB"
        raw.contains("retinoblastoma", true) -> "RB"
        raw.equals("Normal", true) -> "Normal"
        else -> raw
    }

    private fun isPositive(label: String): Boolean =
        label.equals("RB", true) || label.contains("retinoblastoma", true)

    private fun showFlash() {
        flashOverlay.alpha = 0f
        flashOverlay.visibility = View.VISIBLE
        flashOverlay.animate().alpha(1f).setDuration(100).withEndAction {
            flashOverlay.animate().alpha(0f).setDuration(200)
                .withEndAction { flashOverlay.visibility = View.GONE }
        }
    }

    /** Salin file dari assets ke internal storage dan kembalikan absolute path. */
    private fun assetFilePath(assetPath: String): String {
        val outFile = File(filesDir, assetPath.substringAfterLast('/'))
        if (outFile.exists() && outFile.length() > 0) return outFile.absolutePath
        assets.open(assetPath).use { inp ->
            outFile.outputStream().use { out -> inp.copyTo(out) }
        }
        return outFile.absolutePath
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        if (::cameraExecutor.isInitialized) cameraExecutor.shutdown()
    }
}
