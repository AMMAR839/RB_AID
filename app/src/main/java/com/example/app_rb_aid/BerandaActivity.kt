package com.example.app_rb_aid

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.app_rb_aid.databinding.ActivityBerandaBinding
import com.google.firebase.auth.FirebaseAuth
import androidx.core.view.isVisible


class BerandaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBerandaBinding
    private lateinit var auth: FirebaseAuth

    private val CAMERA_REQUEST_CODE = 100
    private val CAMERA_PERMISSION_CODE = 101

    private var fromTutorial = false // Flag untuk cek dari tutorial
    private fun applyModeUi() {
        val isOffline = (ModeManager.mode == ModeManager.Mode.OFFLINE)
        binding.settingButton.visibility = if (isOffline) View.GONE else View.VISIBLE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityBerandaBinding.inflate(layoutInflater)
        setContentView(binding.root) // Gunakan layout maintutorial.xml

        applyModeUi()
        auth = FirebaseAuth.getInstance()


        fromTutorial = intent.getBooleanExtra("FROM_TUTORIAL", false)

        if (fromTutorial) {
            // Jika dari tutorial, overlay & tooltip ditampilkan
            binding.darkOverlayTop.visibility = View.VISIBLE
            binding.darkOverlayBottom.visibility = View.VISIBLE
            binding.tooltipLayout.visibility = View.VISIBLE
        } else {
            // Jika login normal, overlay & tooltip disembunyikan
            binding.darkOverlayTop.visibility = View.GONE
            binding.darkOverlayBottom.visibility = View.GONE
            binding.tooltipLayout.visibility = View.GONE
        }

        // ===== Tombol Kamera =====
        binding.cameraButton.setOnClickListener {
            if (fromTutorial) {
                if (binding.darkOverlayTop.isVisible ||
                    binding.darkOverlayBottom.isVisible
                ) {
                    animateOverlayAndHide()
                } else {
                    // Pindah ke CameraActivity setelah overlay hilang
                    val intent = Intent(this, CameraActivity::class.java)
                    startActivity(intent)
                }
            } else {
                // Jika login normal, langsung buka CameraActivity
                val intent = Intent(this, CameraActivity::class.java)
                startActivity(intent)
            }
        }

        // ===== Tombol Tutorial =====
        binding.tutorialButton.setOnClickListener {
            startActivity(Intent(this, TutorialActivity::class.java))
        }

        // ===== Tombol Logout =====
        binding.btnKeluar.setOnClickListener {
            auth.signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            Toast.makeText(this, "Berhasil keluar", Toast.LENGTH_SHORT).show()
        }

        binding.settingButton.setOnClickListener {
            val intent = Intent(this, GantiNamaActivity::class.java)
            startActivity(intent)
        }
    }

    // ==============================
    // Animasi Overlay
    // ==============================
    private fun animateOverlayAndHide() {
        val moveUp = ObjectAnimator.ofFloat(
            binding.darkOverlayTop,
            "translationY",
            0f,
            -binding.darkOverlayTop.height.toFloat()
        )
        val moveDown = ObjectAnimator.ofFloat(
            binding.darkOverlayBottom,
            "translationY",
            0f,
            binding.darkOverlayBottom.height.toFloat()
        )
        val fadeTooltip = ObjectAnimator.ofFloat(binding.tooltipLayout, "alpha", 1f, 0f)

        moveUp.duration = 600
        moveDown.duration = 600
        fadeTooltip.duration = 400

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(moveUp, moveDown, fadeTooltip)

        animatorSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                binding.darkOverlayTop.visibility = View.GONE
                binding.darkOverlayBottom.visibility = View.GONE
                binding.tooltipLayout.visibility = View.GONE

                // Reset posisi supaya animasi bisa dipakai lagi
                binding.darkOverlayTop.translationY = 0f
                binding.darkOverlayBottom.translationY = 0f
                binding.tooltipLayout.alpha = 1f

            }
        })

        animatorSet.start()
    }

    // ==============================
    // Fungsi Buka Kamera
    // ==============================
    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        } else {
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE)
        }
    }

    // ==============================
    // Hasil Kamera
    // ==============================
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == CAMERA_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val photo = data?.extras?.get("data")
            Toast.makeText(this, "Foto berhasil diambil!", Toast.LENGTH_SHORT).show()
            // TODO: gunakan hasil foto sesuai kebutuhan
        }
    }

    // ==============================
    // Callback Permission
    // ==============================
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                openCamera()
            } else {
                Toast.makeText(this, "Izin kamera ditolak", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
