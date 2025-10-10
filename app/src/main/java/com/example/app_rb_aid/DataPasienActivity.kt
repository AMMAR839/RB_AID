package com.example.app_rb_aid

import android.content.ClipData
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

        // Mode offline tidak lewat sini
        if (ModeManager.mode == ModeManager.Mode.OFFLINE) {
            goToHasilOffline()
            return
        }

        // Minimal 1 mata harus ada
        if (rightUri == null && leftUri == null) {
            Toast.makeText(this, "Minimal pilih/ambil 1 mata terlebih dahulu.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContentView(R.layout.activity_data_pasien)

        etNama = findViewById(R.id.PasienNama)
        etNik = findViewById(R.id.PasienNIK)
        etTanggal = findViewById(R.id.PasienTanggal)
        tilTanggal = findViewById(R.id.tilPasienTanggal)
        btnSimpan = findViewById(R.id.button_simpan)

        // Picker Tanggal Lahir
        etTanggal.setOnClickListener { showTanggalPicker(etTanggal, tilTanggal) }
        tilTanggal.setEndIconOnClickListener { etTanggal.performClick() }
        etTanggal.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) etTanggal.performClick() }

        // Simpan → langsung ke HasilActivity (upload dilakukan di HasilActivity)
        btnSimpan.setOnClickListener {
            val nama = etNama.text.toString().trim()
            val nik = etNik.text.toString().trim()
            val tanggal = etTanggal.text.toString().trim() // DOB

            if (nama.isEmpty() || nik.isEmpty() || tanggal.isEmpty()) {
                Toast.makeText(this, "Lengkapi data pasien", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Timestamp pemeriksaan (sekarang)
            val now = Date()
            val fmtDate = SimpleDateFormat("dd/MM/yyyy", Locale("id","ID"))
            val fmtTime = SimpleDateFormat("HH:mm 'WIB'", Locale("id","ID"))
            val examDate = fmtDate.format(now)
            val examTime = fmtTime.format(now)

            val intent = Intent(this, HasilActivity::class.java).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                putExtra("EXTRA_NAMA", nama)
                putExtra("EXTRA_NIK", nik)

                // DOB eksplisit + kompat lama
                putExtra("EXTRA_TANGGAL", tanggal)
                putExtra("EXTRA_TANGGAL_LAHIR", tanggal)

                // Jadwal pemeriksaan
                putExtra("EXTRA_TANGGAL_PEMERIKSAAN", examDate)
                putExtra("EXTRA_WAKTU_PEMERIKSAAN",   examTime)

                // Hasil model per-mata
                putExtra("RIGHT_EYE_URI", rightUri)
                putExtra("LEFT_EYE_URI", leftUri)
                putExtra("RIGHT_LABEL", rightLabel)
                putExtra("RIGHT_SCORE", rightScore)
                putExtra("LEFT_LABEL", leftLabel)
                putExtra("LEFT_SCORE", leftScore)

                // Beri tahu HasilActivity untuk upload & simpan
                putExtra("NEEDS_UPLOAD", true)

                val uris = mutableListOf<Uri>()
                rightUri?.let { uris.add(Uri.parse(it)) }
                leftUri ?.let { uris.add(Uri.parse(it)) }
                if (uris.isNotEmpty()) {
                    clipData = ClipData.newUri(contentResolver, "eye", uris[0])
                    for (i in 1 until uris.size) clipData!!.addItem(ClipData.Item(uris[i]))
                }
            }
            startActivity(intent)
            finish()
        }
    }

    // === Date picker ===
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

    // (Tetap disimpan bila kamu masih butuh di tempat lain)
    private suspend fun saveToFirebase(nama: String, nik: String, tanggal: String) = withContext(Dispatchers.IO) {
        try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"
            val storage = FirebaseStorage.getInstance().reference
            val db = FirebaseFirestore.getInstance()

            val pasienId = "${nama.replace(" ", "_")}_${System.currentTimeMillis()}"
            val baseRef = storage.child("rb_images/$uid/$pasienId")

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

            val doc = mutableMapOf<String, Any?>(
                "nama" to nama,
                "nik" to nik,
                "tanggal" to tanggal,
                "created_at" to System.currentTimeMillis()
            )

            rightLabel?.let { doc["hasil_kanan"] = it }
            if (rightScore >= 0f) doc["confidence_kanan"] = rightScore
            leftLabel?.let { doc["hasil_kiri"] = it }
            if (leftScore >= 0f) doc["confidence_kiri"] = leftScore

            rightUrl?.let { doc["foto_kanan_url"] = it }
            leftUrl?.let  { doc["foto_kiri_url"]  = it }

            db.collection("users").document(uid)
                .collection("pasien").document(pasienId)
                .set(doc).await()

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
        val now = Date()
        val fmtDate = SimpleDateFormat("dd/MM/yyyy", Locale("id","ID"))
        val fmtTime = SimpleDateFormat("HH:mm 'WIB'", Locale("id","ID"))

        val intent = Intent(this, HasilActivity::class.java).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            putExtra("EXTRA_NAMA", nama)
            putExtra("EXTRA_NIK", nik)

            putExtra("EXTRA_TANGGAL", tanggal)
            putExtra("EXTRA_TANGGAL_LAHIR", tanggal)

            putExtra("EXTRA_TANGGAL_PEMERIKSAAN", fmtDate.format(now))
            putExtra("EXTRA_WAKTU_PEMERIKSAAN",   fmtTime.format(now))

            putExtra("RIGHT_EYE_URI", rightUri)
            putExtra("LEFT_EYE_URI", leftUri)
            putExtra("RIGHT_LABEL", rightLabel)
            putExtra("RIGHT_SCORE", rightScore)
            putExtra("LEFT_LABEL", leftLabel)
            putExtra("LEFT_SCORE", leftScore)

            putExtra("RIGHT_URL", rightUrl)
            putExtra("LEFT_URL", leftUrl)

            putExtra("DIAGNOSIS", diagnosis)

            val uris = mutableListOf<Uri>()
            rightUri?.let { uris.add(Uri.parse(it)) }
            leftUri ?.let { uris.add(Uri.parse(it)) }
            if (uris.isNotEmpty()) {
                clipData = ClipData.newUri(contentResolver, "eye", uris[0])
                for (i in 1 until uris.size) clipData!!.addItem(ClipData.Item(uris[i]))
            }
        }
        startActivity(intent)
        finish()
    }

    private fun goToHasilOffline() {
        val diagnosis = buildDiagnosis(rightLabel, leftLabel)
        val now = Date()
        val fmtDate = SimpleDateFormat("dd/MM/yyyy", Locale("id","ID"))
        val fmtTime = SimpleDateFormat("HH:mm 'WIB'", Locale("id","ID"))

        val intent = Intent(this, HasilActivity::class.java).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            putExtra("EXTRA_NAMA", "Mode Offline")
            putExtra("EXTRA_NIK", "")
            putExtra("EXTRA_TANGGAL", "")
            putExtra("EXTRA_TANGGAL_LAHIR", "")

            putExtra("EXTRA_TANGGAL_PEMERIKSAAN", fmtDate.format(now))
            putExtra("EXTRA_WAKTU_PEMERIKSAAN",   fmtTime.format(now))

            putExtra("RIGHT_EYE_URI", rightUri)
            putExtra("LEFT_EYE_URI", leftUri)
            putExtra("RIGHT_LABEL", rightLabel)
            putExtra("RIGHT_SCORE", rightScore)
            putExtra("LEFT_LABEL", leftLabel)
            putExtra("LEFT_SCORE", leftScore)

            putExtra("DIAGNOSIS", diagnosis)

            val uris = mutableListOf<Uri>()
            rightUri?.let { uris.add(Uri.parse(it)) }
            leftUri ?.let { uris.add(Uri.parse(it)) }
            if (uris.isNotEmpty()) {
                clipData = ClipData.newUri(contentResolver, "eye", uris[0])
                for (i in 1 until uris.size) clipData!!.addItem(ClipData.Item(uris[i]))
            }
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
