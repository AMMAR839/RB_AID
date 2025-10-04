package com.example.app_rb_aid

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    // Views
    private lateinit var previewView: androidx.camera.view.PreviewView
    private lateinit var instructionText: TextView
    private lateinit var flashOverlay: View
    private lateinit var overlayView: CameraOverlayView // <-- NEW

    // Mode bidik (jepret)
    private lateinit var captureFab: FloatingActionButton

    // Mode review
    private lateinit var capturedImageView: ImageView
    private lateinit var reviewActions: LinearLayout
    private lateinit var redoFab: FloatingActionButton
    private lateinit var confirmFab: FloatingActionButton

    // CameraX
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    // State proses mata
    private var currentEye = "RIGHT"
    private var rightEyeUri: Uri? = null
    private var leftEyeUri: Uri? = null

    // State review
    private var isReviewMode = false
    private var pendingPhotoUri: Uri? = null
    private var lastPhotoFile: File? = null

    // Permission
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) startCamera() else {
                Toast.makeText(this, "Izin kamera ditolak", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

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

        // Permission kamera
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        // Actions
        captureFab.setOnClickListener { takePhoto() }
        redoFab.setOnClickListener { exitReview(deleteFile = true) }
        confirmFab.setOnClickListener { confirmPhotoAndProceed() }
    }

    //===== Camera =====
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder().build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Log.e("CameraActivity", "Gagal memulai kamera: ${e.message}", e)
                Toast.makeText(this, "Gagal memulai kamera", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        if (isReviewMode) return

        // === Cara utama: snapshot preview + crop ROI dari overlay ===
        captureRoiFromPreview()?.let { roiFile ->
            showFlash()
            lastPhotoFile = roiFile
            enterReview(Uri.fromFile(roiFile))
            return
        }

        // === Fallback: pakai ImageCapture biasa kalau snapshot belum siap ===
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

    /** Ambil bitmap dari PreviewView, crop sesuai kotak overlay, simpan JPG di cache. */
    private fun captureRoiFromPreview(): File? {
        val bmp: Bitmap = previewView.bitmap ?: run {
            Toast.makeText(this, "Frame belum siap. Coba lagi...", Toast.LENGTH_SHORT).show()
            return null
        }

        val box = overlayView.getBoxRect()
        // Clamp biar aman
        val left   = box.left.coerceIn(0f, bmp.width - 1f)
        val top    = box.top .coerceIn(0f, bmp.height - 1f)
        val right  = box.right.coerceIn(left + 1f, bmp.width.toFloat())
        val bottom = box.bottom.coerceIn(top + 1f, bmp.height.toFloat())

        val cropW = (right - left).toInt()
        val cropH = (bottom - top).toInt()
        if (cropW < 4 || cropH < 4) {
            Toast.makeText(this, "Kotak terlalu kecil", Toast.LENGTH_SHORT).show()
            return null
        }

        val cropped = Bitmap.createBitmap(bmp, left.toInt(), top.toInt(), cropW, cropH)

        // (Opsional) langsung resize ke 224x224 agar konsisten dengan model:
        // val resized = Bitmap.createScaledBitmap(cropped, 224, 224, true)

        val dir = externalCacheDir ?: cacheDir
        val outFile = File(dir, "roi_${System.currentTimeMillis()}.jpg")
        FileOutputStream(outFile).use { fos ->
            cropped.compress(Bitmap.CompressFormat.JPEG, 95, fos)
        }
        return outFile
    }

    //===== Mode Review =====
    private fun enterReview(uri: Uri) {
        isReviewMode = true
        pendingPhotoUri = uri

        capturedImageView.setImageURI(uri)
        capturedImageView.visibility = View.VISIBLE

        reviewActions.visibility = View.VISIBLE
        captureFab.visibility = View.GONE

        instructionText.text = if (currentEye == "RIGHT")
            "Periksa foto mata kanan"
        else
            "Periksa foto mata kiri"
    }

    private fun exitReview(deleteFile: Boolean) {
        if (deleteFile) {
            try { lastPhotoFile?.delete() } catch (_: Exception) {}
        }
        isReviewMode = false
        pendingPhotoUri = null

        capturedImageView.setImageDrawable(null)
        capturedImageView.visibility = View.GONE

        reviewActions.visibility = View.GONE
        captureFab.visibility = View.VISIBLE

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
            goToResult()
        }
    }

    //===== UI efek =====
    private fun showFlash() {
        flashOverlay.alpha = 0f
        flashOverlay.visibility = View.VISIBLE
        flashOverlay.animate()
            .alpha(1f).setDuration(100)
            .withEndAction {
                flashOverlay.animate()
                    .alpha(0f).setDuration(200)
                    .withEndAction { flashOverlay.visibility = View.GONE }
            }
    }

    //===== Hasil =====
    private fun goToResult() {
        val intent = Intent(this, DataPasienActivity::class.java).apply {
            putExtra("RIGHT_EYE_URI", rightEyeUri?.toString())
            putExtra("LEFT_EYE_URI", leftEyeUri?.toString())
        }
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
