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

    // dari CameraActivity (SEKARANG BOLEH NULL)
    private var rightUri: String? = null
    private var leftUri: String? = null
    private var rightLabel: String? = null
    private var rightScore: Float = -1f
    private var leftLabel: String? = null
    private var leftScore: Float = -1f

    private val sdfDisplay = SimpleDateFormat("dd/MM/yyyy", Locale("id", "ID")).apply { isLenient = false }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ambil extras dari CameraActivity
        rightUri   = intent.getStringExtra("RIGHT_EYE_URI")
        leftUri    = intent.getStringExtra("LEFT_EYE_URI")
        rightLabel = intent.getStringExtra("RIGHT_LABEL")
        rightScore = intent.getFloatExtra("RIGHT_SCORE", -1f)
        leftLabel  = intent.getStringExtra("LEFT_LABEL")
        leftScore  = intent.getFloatExtra("LEFT_SCORE", -1f)

        // Mode offline seharusnya tidak lewat sini (CameraActivity langsung ke HasilActivity),
        // tapi kalaupun lewat, tetap aman:
        if (ModeManager.mode == ModeManager.Mode.OFFLINE) {
            goToHasilOffline()
            return
        }

        // Minimal 1 mata harus ada untuk online flow
        if (rightUri == null && leftUri == null) {
            Toast.makeText(this, "Minimal pilih/ambil 1 mata terlebih dahulu.", Toast.LENGTH_LONG).show()
            finish()
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
        // di onCreate -> btnSimpan.setOnClickListener
        btnSimpan.setOnClickListener {
            val nama = etNama.text.toString().trim()
            val nik = etNik.text.toString().trim()
            val tanggal = etTanggal.text.toString().trim()

            if (nama.isEmpty() || nik.isEmpty() || tanggal.isEmpty()) {
                Toast.makeText(this, "Lengkapi data pasien", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Langsung ke HasilActivity (tanpa nunggu upload)
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

                // Beri tahu HasilActivity untuk melakukan upload & simpan
                putExtra("NEEDS_UPLOAD", true)
            }
            startActivity(intent)
            finish() // DataPasienActivity selesai, user langsung lihat hasil
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

            // Upload hanya yang ada
            var rightUrl: String? = null
            var leftUrl: String? = null

            if (!rightUri.isNullOrEmpty()) {
                val rightRef = baseRef.child("right_eye.jpg")
                rightUrl = rightRef.putFile(Uri.parse(rightUri))
                    .continueWithTask { rightRef.downloadUrl }
                    .await()
                    .toString()
            }
            if (!leftUri.isNullOrEmpty()) {
                val leftRef  = baseRef.child("left_eye.jpg")
                leftUrl = leftRef.putFile(Uri.parse(leftUri))
                    .continueWithTask { leftRef.downloadUrl }
                    .await()
                    .toString()
            }

            // Build dokumen hanya dengan field yang ada
            val doc = mutableMapOf<String, Any?>(
                "nama" to nama,
                "nik" to nik,
                "tanggal" to tanggal,
                "created_at" to System.currentTimeMillis()
            )

            // Label & score yang tersedia
            rightLabel?.let { doc["hasil_kanan"] = it }
            if (rightScore >= 0f) doc["confidence_kanan"] = rightScore
            leftLabel?.let { doc["hasil_kiri"] = it }
            if (leftScore >= 0f) doc["confidence_kiri"] = leftScore

            // URL yang tersedia
            rightUrl?.let { doc["foto_kanan_url"] = it }
            leftUrl?.let  { doc["foto_kiri_url"]  = it }

            db.collection("users").document(uid)
                .collection("pasien").document(pasienId)
                .set(doc).await()

            // Hitung diagnosis buat tampilan hasil
            val diagnosis = buildDiagnosis(rightLabel, leftLabel)

            withContext(Dispatchers.Main) {
                goToHasilOnline(nama, nik, tanggal, rightUrl, leftUrl, diagnosis)
            }

        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@DataPasienActivity, "Gagal simpan: ${e.message}", Toast.LENGTH_LONG).show()
                val diagnosis = buildDiagnosis(rightLabel, leftLabel)
                goToHasilOnline(nama, nik, tanggal, null, null, diagnosis)
            }
        }
    }

    private fun goToHasilOnline(
        nama: String,
        nik: String,
        tanggal: String,
        rightUrl: String?,
        leftUrl: String?,
        diagnosis: String
    ) {
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

            putExtra("DIAGNOSIS", diagnosis)
        }
        startActivity(intent)
        finish()
    }

    private fun goToHasilOffline() {
        val diagnosis = buildDiagnosis(rightLabel, leftLabel)
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

            putExtra("DIAGNOSIS", diagnosis)
        }
        startActivity(intent)
        finish()
    }

    private fun buildDiagnosis(rLabel: String?, lLabel: String?): String {
        fun pos(s: String?) = s?.equals("RB", true) == true || (s?.contains("retinoblastoma", true) == true)
        val hasRB = pos(rLabel) || pos(lLabel)
        return if (hasRB) "Terindikasi retinoblastoma (cek ke dokter)." else "Tidak terindikasi retinoblastoma."
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
