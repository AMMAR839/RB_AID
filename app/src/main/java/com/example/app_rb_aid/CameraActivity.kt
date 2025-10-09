package com.example.app_rb_aid

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
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
import java.io.InputStream
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
    private lateinit var pickFab: FloatingActionButton
    private lateinit var skipFab: FloatingActionButton

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

    // PyTorch model lokal (.pt) – taruh di assets/ (atau assets/models/ lalu sesuaikan path)
    private var localModel: Module? = null
    private val inputSize = 64
    private val MEAN = floatArrayOf(2.3147659e-05f, -5.0520233e-05f, 1.3798560e-05f)
    private val STD  = floatArrayOf(0.8244203f, 0.8003612f, 0.7935678f)

    // ---------- Coroutines ----------
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ---------- Permissions ----------
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            if (ok) startCamera() else {
                Toast.makeText(this, "Izin kamera ditolak", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

    // ---------- Gallery picker ----------
    private val galleryPicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            enterReview(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        isOffline = (ModeManager.mode == ModeManager.Mode.OFFLINE)

        // Bind views
        previewView = findViewById(R.id.previewView)
        instructionText = findViewById(R.id.instructionText)
        flashOverlay = findViewById(R.id.flashOverlay)
        overlayView = findViewById(R.id.CameraOverlayView)

        captureFab = findViewById(R.id.captureFab)
        pickFab    = findViewById(R.id.pickFab)
        skipFab    = findViewById(R.id.skipFab)

        capturedImageView = findViewById(R.id.capturedImageView)
        reviewActions = findViewById(R.id.reviewActions)
        redoFab = findViewById(R.id.redoFab)
        confirmFab = findViewById(R.id.confirmFab)

        cameraExecutor = Executors.newSingleThreadExecutor()
        instructionText.text = "Ambil foto mata kanan"

        // Load model lokal bila offline.
        if (isOffline) {
            ensureLocalModelLoaded()
        }

        // Permission kamera
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }

        // Actions
        captureFab.setOnClickListener { takePhoto() }
        pickFab.setOnClickListener { galleryPicker.launch("image/*") }
        skipFab.setOnClickListener { onSkipCurrentEye() }
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

        // Coba snapshot dari PreviewView + crop ROI
        captureRoiFromPreview()?.let { roiFile ->
            showFlash()
            lastPhotoFile = roiFile
            enterReview(Uri.fromFile(roiFile))
            return
        }

        // Fallback: ImageCapture
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

    /** Ambil bitmap dari PreviewView, crop sesuai kotak overlay, simpan JPG. */
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
        val out = File((externalCacheDir ?: cacheDir), "roi_${System.currentTimeMillis()}.jpg")
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

        // Sembunyikan tombol saat preview
        capturedImageView.visibility = View.VISIBLE
        reviewActions.visibility = View.VISIBLE
        captureFab.visibility = View.GONE
        pickFab.visibility = View.GONE
        skipFab.visibility = View.GONE
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

        // Tampilkan kembali tombol
        captureFab.visibility = View.VISIBLE
        pickFab.visibility = View.VISIBLE
        skipFab.visibility = View.VISIBLE
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

    private fun onSkipCurrentEye() {
        if (currentEye == "RIGHT") {
            rightEyeUri = null
            currentEye = "LEFT"
            instructionText.text = "Ambil foto mata kiri"
        } else {
            leftEyeUri = null
            onBothPhotosReady()
        }
    }

    // ---------------- Setelah dua mata diputuskan ----------------
    private fun onBothPhotosReady() {
        val r = rightEyeUri
        val l = leftEyeUri
        if (r == null && l == null) {
            Toast.makeText(this, "Minimal ambil/pilih 1 mata.", Toast.LENGTH_LONG).show()
            return
        }

        scope.launch {
            val (rawRLabel, rScore) = if (r != null) runInference(r) else "Unknown" to -1f
            val (rawLLabel, lScore) = if (l != null) runInference(l) else "Unknown" to -1f

            val rLabel = normalizeLabel(rawRLabel)
            val lLabel = normalizeLabel(rawLLabel)

            val diagnosis = if (isPositive(rLabel) || isPositive(lLabel))
                "Terindikasi retinoblastoma (cek ke dokter)." else
                "Tidak terindikasi retinoblastoma."

            if (isOffline) {
                // OFFLINE → langsung ke HasilActivity
                val go = Intent(this@CameraActivity, HasilActivity::class.java).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    putExtra("EXTRA_NAMA", "Mode Offline")
                    putExtra("EXTRA_NIK", "")
                    putExtra("EXTRA_TANGGAL", "")

                    putExtra("RIGHT_EYE_URI", r?.toString())
                    putExtra("LEFT_EYE_URI",  l?.toString())

                    putExtra("RIGHT_LABEL", rLabel)
                    putExtra("RIGHT_SCORE", rScore)
                    putExtra("LEFT_LABEL",  lLabel)
                    putExtra("LEFT_SCORE",  lScore)

                    putExtra("DIAGNOSIS", diagnosis)
                }
                startActivity(go)
                finish()
            } else {
                // ONLINE → lanjut isi data
                val go = Intent(this@CameraActivity, DataPasienActivity::class.java).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    putExtra("RIGHT_EYE_URI", r?.toString())
                    putExtra("LEFT_EYE_URI",  l?.toString())
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
        // ONLINE lebih dulu kalau mode online
        if (!isOffline) {
            try {
                val f = prepareJpegForUpload(uri) // aman untuk HEIC/PNG/large
                val body = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", f.name, f.asRequestBody("image/jpeg".toMediaType()))
                    .build()
                val req = Request.Builder().url(cloudUrl).post(body).build()
                http.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bodyStr = resp.body?.string().orEmpty()
                        val json = JSONObject(bodyStr)
                        val label = json.optString("prediction", "Unknown")
                        val conf  = json.optDouble("confidence", 0.0).toFloat()
                        return@withContext label to conf
                    } else {
                        Log.w("CameraActivity", "Cloud HTTP ${resp.code} → fallback offline.")
                    }
                }
            } catch (e: Exception) {
                Log.w("CameraActivity", "Cloud error: ${e.message} → fallback offline.", e)
            }
        }

        // OFFLINE (PyTorch) — pastikan model terload meski mode online
        ensureLocalModelLoaded()
        localModel?.let { model ->
            try {
                val bmp = decodeBitmapScaled(uri, maxDim = 512) // hemat memori
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
        return@withContext "Unknown" to -1f
    }

    // --- Helpers aman untuk content:// & memori ---
    private fun uriToTempFile(uri: Uri): File {
        val outFile = File.createTempFile("picked_", ".bin", cacheDir)
        contentResolver.openInputStream(uri)?.use { input ->
            outFile.outputStream().use { out -> input.copyTo(out) }
        } ?: throw IllegalStateException("Tidak bisa buka stream dari URI: $uri")
        return outFile
    }

    /** Decode aman & di-downscale saat decode untuk hemat memori. */
    private fun decodeBitmapScaled(uri: Uri, maxDim: Int = 1024): Bitmap {
        return if (Build.VERSION.SDK_INT >= 28) {
            val src = ImageDecoder.createSource(contentResolver, uri)
            ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                val (w, h) = info.size.width to info.size.height
                val scale = if (w >= h) maxDim.toFloat() / w else maxDim.toFloat() / h
                val targetW = max(1, (w * scale).toInt())
                val targetH = max(1, (h * scale).toInt())
                decoder.setTargetSize(targetW, targetH)
            }
        } else {
            // 2-pass decode untuk inSampleSize
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            } ?: throw IllegalStateException("Tidak bisa baca metadata gambar: $uri")

            val sample = calculateInSampleSize(bounds, maxDim, maxDim)
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
                    ?: throw IllegalStateException("Gagal decode bitmap: $uri")
            } ?: throw IllegalStateException("Tidak bisa buka stream: $uri")
        }
    }

    private fun calculateInSampleSize(o: BitmapFactory.Options, reqW: Int, reqH: Int): Int {
        val h = o.outHeight
        val w = o.outWidth
        var inSampleSize = 1
        if (h > reqH || w > reqW) {
            var halfH = h / 2
            var halfW = w / 2
            while ((halfH / inSampleSize) >= reqH && (halfW / inSampleSize) >= reqW) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /** Siapkan file JPEG untuk upload: konversi non-JPEG (HEIC/PNG) → JPEG. */
    private fun prepareJpegForUpload(uri: Uri): File {
        val mime = contentResolver.getType(uri)?.lowercase() ?: "image/jpeg"
        return if (mime == "image/jpeg" || mime == "image/jpg") {
            // Salin apa adanya ke file temp .jpg
            val outFile = File.createTempFile("upload_", ".jpg", cacheDir)
            contentResolver.openInputStream(uri)?.use { input ->
                outFile.outputStream().use { out -> input.copyTo(out) }
            } ?: throw IllegalStateException("Tidak bisa open stream: $uri")
            outFile
        } else {
            // Decode + convert ke JPEG
            val bmp = decodeBitmapScaled(uri, maxDim = 2048)
            val outFile = File.createTempFile("upload_", ".jpg", cacheDir)
            FileOutputStream(outFile).use { fos ->
                if (!bmp.compress(Bitmap.CompressFormat.JPEG, 92, fos)) {
                    throw IllegalStateException("Gagal kompres JPEG")
                }
            }
            outFile
        }
    }

    // --- preprocess PyTorch (CHW) ---
    private fun bitmapToNormalizedCHW(bmp: Bitmap, mean: FloatArray, std: FloatArray): Tensor {
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

    /** Pastikan model lokal siap dipakai untuk fallback. */
    private fun ensureLocalModelLoaded() {
        if (localModel == null) {
            try {
                val path = assetFilePath("cnn2_precise_best.pt")
                localModel = Module.load(path)
                Log.d("CameraActivity", "Model lokal PyTorch dimuat.")
            } catch (e: Exception) {
                Log.e("CameraActivity", "Gagal load model lokal: ${e.message}", e)
                localModel = null
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        if (::cameraExecutor.isInitialized) cameraExecutor.shutdown()
    }
}
