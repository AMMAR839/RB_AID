package com.example.app_rb_aid

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class DataPasienActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var etNama: TextInputEditText
    private lateinit var etNik: TextInputEditText
    private lateinit var etTanggal: TextInputEditText
    private lateinit var tilTanggal: TextInputLayout
    private lateinit var btnSimpan: MaterialButton

    // dari CameraActivity
    private lateinit var rightUri: String
    private lateinit var leftUri: String
    private lateinit var rightLabel: String
    private var rightScore: Float = -1f
    private lateinit var leftLabel: String
    private var leftScore: Float = -1f

    private val sdfDisplay = SimpleDateFormat("dd/MM/yyyy", Locale("id", "ID")).apply { isLenient = false }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // === Mode offline langsung skip ke hasil ===
        val isOffline = ModeManager.mode == ModeManager.Mode.OFFLINE

        rightUri   = intent.getStringExtra("RIGHT_EYE_URI") ?: ""
        leftUri    = intent.getStringExtra("LEFT_EYE_URI") ?: ""
        rightLabel = intent.getStringExtra("RIGHT_LABEL") ?: "Unknown"
        rightScore = intent.getFloatExtra("RIGHT_SCORE", -1f)
        leftLabel  = intent.getStringExtra("LEFT_LABEL") ?: "Unknown"
        leftScore  = intent.getFloatExtra("LEFT_SCORE", -1f)

        // Kalau OFFLINE → langsung ke hasil, tanpa form
        if (isOffline) {
            goToHasilOffline()
            return
        }

        // === ONLINE mode → render UI ===
        setContentView(R.layout.activity_data_pasien)

        etNama = findViewById(R.id.PasienNama)
        etNik = findViewById(R.id.PasienNIK)
        etTanggal = findViewById(R.id.PasienTanggal)
        tilTanggal = findViewById(R.id.tilPasienTanggal)
        btnSimpan = findViewById(R.id.button_simpan)

        // === Picker Tanggal ===
        etTanggal.setOnClickListener { showTanggalPicker(etTanggal, tilTanggal) }
        tilTanggal.setEndIconOnClickListener { etTanggal.performClick() }
        etTanggal.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) etTanggal.performClick() }

        // === Tombol Simpan ===
        btnSimpan.setOnClickListener {
            val nama = etNama.text.toString().trim()
            val nik = etNik.text.toString().trim()
            val tanggal = etTanggal.text.toString().trim()

            if (nama.isEmpty() || nik.isEmpty() || tanggal.isEmpty()) {
                Toast.makeText(this, "Lengkapi data pasien", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            scope.launch { saveToFirebase(nama, nik, tanggal) }
        }
    }

    // === Fungsi menampilkan date picker ===
    private fun showTanggalPicker(target: TextInputEditText, til: TextInputLayout?) {
        val start1900 = Calendar.getInstance().apply {
            set(1900, Calendar.JANUARY, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val constraints = CalendarConstraints.Builder()
            .setValidator(DateValidatorPointBackward.now())
            .setStart(start1900)
            .setEnd(System.currentTimeMillis())
            .build()

        val defaultSelection = parseDateToMillis(target.text?.toString())

        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Pilih tanggal lahir")
            .setCalendarConstraints(constraints)
            .apply { defaultSelection?.let { setSelection(it) } }
            .build()

        picker.addOnPositiveButtonClickListener { selection ->
            val dateStr = sdfDisplay.format(Date(selection))
            target.setText(dateStr)
            til?.error = null
        }

        picker.show(supportFragmentManager, "tgl_picker")
    }

    private fun parseDateToMillis(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        return try {
            sdfDisplay.parse(text)?.time
        } catch (_: ParseException) {
            null
        }
    }

    // === Simpan ke Firebase (online mode) ===
    private suspend fun saveToFirebase(nama: String, nik: String, tanggal: String) = withContext(Dispatchers.IO) {
        try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"
            val storage = FirebaseStorage.getInstance().reference
            val db = FirebaseFirestore.getInstance()

            val pasienId = "${nama.replace(" ", "_")}_${System.currentTimeMillis()}"
            val baseRef = storage.child("rb_images/$uid/$pasienId")

            val rightRef = baseRef.child("right_eye.jpg")
            val leftRef  = baseRef.child("left_eye.jpg")

            val rightTask = rightRef.putFile(Uri.parse(rightUri)).continueWithTask { rightRef.downloadUrl }
            val leftTask  = leftRef.putFile(Uri.parse(leftUri)).continueWithTask { leftRef.downloadUrl }

            val rightUrl = rightTask.await().toString()
            val leftUrl  = leftTask.await().toString()

            val doc = hashMapOf(
                "nama" to nama,
                "nik" to nik,
                "tanggal" to tanggal,
                "hasil_kanan" to rightLabel,
                "hasil_kiri" to leftLabel,
                "confidence_kanan" to rightScore,
                "confidence_kiri" to leftScore,
                "foto_kanan_url" to rightUrl,
                "foto_kiri_url" to leftUrl,
                "created_at" to System.currentTimeMillis()
            )
            db.collection("users").document(uid)
                .collection("pasien").document(pasienId)
                .set(doc).await()

            withContext(Dispatchers.Main) {
                goToHasilOnline(nama, nik, tanggal, rightUrl, leftUrl)
            }

        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@DataPasienActivity, "Gagal simpan: ${e.message}", Toast.LENGTH_LONG).show()
                goToHasilOnline(nama, nik, tanggal, null, null)
            }
        }
    }

    // === Pindah ke hasil (online) ===
    private fun goToHasilOnline(nama: String, nik: String, tanggal: String, rightUrl: String?, leftUrl: String?) {
        val intent = Intent(this, HasilActivity::class.java).apply {
            putExtra("EXTRA_NAMA", nama)
            putExtra("EXTRA_NIK", nik)
            putExtra("EXTRA_TANGGAL", tanggal)
            putExtra("RIGHT_EYE_URI", rightUri)
            putExtra("LEFT_EYE_URI", leftUri)
            putExtra("RIGHT_LABEL", rightLabel)
            putExtra("RIGHT_SCORE", rightScore)
            putExtra("LEFT_LABEL", leftLabel)
            putExtra("LEFT_SCORE", leftScore)
            putExtra("RIGHT_URL", rightUrl)
            putExtra("LEFT_URL", leftUrl)
        }
        startActivity(intent)
        finish()
    }

    // === Pindah ke hasil (offline) ===
    private fun goToHasilOffline() {
        val intent = Intent(this, HasilActivity::class.java).apply {
            putExtra("EXTRA_NAMA", "Mode Offline")
            putExtra("EXTRA_NIK", "")
            putExtra("EXTRA_TANGGAL", "")
            putExtra("RIGHT_EYE_URI", rightUri)
            putExtra("LEFT_EYE_URI", leftUri)
            putExtra("RIGHT_LABEL", rightLabel)
            putExtra("RIGHT_SCORE", rightScore)
            putExtra("LEFT_LABEL", leftLabel)
            putExtra("LEFT_SCORE", leftScore)
        }
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
