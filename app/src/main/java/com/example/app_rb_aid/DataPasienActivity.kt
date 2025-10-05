package com.example.app_rb_aid

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class DataPasienActivity : AppCompatActivity() {

    // ---------- Util tanggal ----------
    private val localeID = Locale("id", "ID")
    private val sdfDisplay = SimpleDateFormat("dd/MM/yyyy", localeID).apply { isLenient = false }

    // ---------- Firebase ----------
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ambil URI foto dari Intent (WAJIB dikirim dari step foto)
        val rightUriExtra = intent.getStringExtra("RIGHT_EYE_URI")
        val leftUriExtra  = intent.getStringExtra("LEFT_EYE_URI")

        // === SHORT-CIRCUIT OFFLINE: JANGAN render UI, langsung inferensi ===
        if (ModeManager.mode == ModeManager.Mode.OFFLINE) {
            if (rightUriExtra.isNullOrEmpty() || leftUriExtra.isNullOrEmpty()) {
                // Tidak ada foto → akhiri tanpa animasi
                finish()
                overridePendingTransition(0, 0)
                return
            }
            lifecycleScope.launch {
                runOfflineFlow(rightUriExtra, leftUriExtra)
                overridePendingTransition(0, 0)
            }
            return
        }

        // === ONLINE MODE → render UI form ===
        setContentView(R.layout.activity_data_pasien)

        // UI refs (samakan ID dengan layout-mu)
        findViewById<ImageView>(R.id.back_button_data_pasien).setOnClickListener {
            finish()
        }
        val etNama     = findViewById<TextInputEditText>(R.id.PasienNama)
        val etNik      = findViewById<TextInputEditText>(R.id.PasienNIK)
        val etTgl      = findViewById<TextInputEditText>(R.id.PasienTanggal)
        val btnSimpan  = findViewById<MaterialButton>(R.id.button_simpan)

        val tilNama: TextInputLayout? = findViewById(R.id.tilPasienNama)
        val tilNik : TextInputLayout? = findViewById(R.id.tilPasienNIK)
        val tilTgl : TextInputLayout? = findViewById(R.id.tilPasienTanggal)

        // Bersihkan error saat user mengetik
        etNama.doAfterTextChanged { tilNama?.error = null; etNama.error = null }
        etNik.doAfterTextChanged  { tilNik?.error  = null; etNik.error  = null }
        etTgl.doAfterTextChanged  { tilTgl?.error  = null; etTgl.error  = null }

        // DatePicker
        etTgl.setOnClickListener { showTanggalPicker(etTgl, tilTgl) }
        tilTgl?.setEndIconOnClickListener { etTgl.performClick() }
        etTgl.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) etTgl.performClick() }

        // Simpan + inferensi + save Firestore + buka hasil
        btnSimpan.setOnClickListener {
            val nama = etNama.text?.toString()?.trim().orEmpty()
            val nik  = etNik.text?.toString()?.trim().orEmpty()
            val tgl  = etTgl.text?.toString()?.trim().orEmpty()

            var valid = true

            // Validasi nama
            if (nama.isEmpty()) {
                tilNama?.error = "Nama wajib diisi"
                if (valid) etNama.requestFocus()
                valid = false
            }

            // Validasi NIK 16 digit
            val nikValid = nik.matches(Regex("\\d{16}"))
            if (nik.isEmpty()) {
                tilNik?.error = "NIK wajib diisi"
                if (valid) etNik.requestFocus()
                valid = false
            } else if (!nikValid) {
                tilNik?.error = "NIK harus 16 digit angka"
                if (valid) etNik.requestFocus()
                valid = false
            }

            // Validasi tanggal format dd/MM/yyyy & tidak di masa depan
            val tglMillis = parseDateToMillis(tgl)
            if (tgl.isEmpty()) {
                tilTgl?.error = "Tanggal lahir wajib diisi"
                if (valid) etTgl.requestFocus()
                valid = false
            } else if (tglMillis == null) {
                tilTgl?.error = "Format tanggal tidak valid (pakai dd/MM/yyyy)"
                if (valid) etTgl.requestFocus()
                valid = false
            } else if (tglMillis > System.currentTimeMillis()) {
                tilTgl?.error = "Tanggal lahir tidak boleh di masa depan"
                if (valid) etTgl.requestFocus()
                valid = false
            }

            // Pastikan URI foto ada
            if (rightUriExtra.isNullOrEmpty() || leftUriExtra.isNullOrEmpty()) {
                Toast.makeText(this, "Foto mata kanan & kiri wajib diambil", Toast.LENGTH_SHORT).show()
                valid = false
            }

            if (!valid) return@setOnClickListener

            lifecycleScope.launch {
                runOnlineFlowSaveAndGo(
                    nama = nama,
                    nik = nik,
                    tglMillis = tglMillis!!,
                    rightUri = rightUriExtra!!,
                    leftUri = leftUriExtra!!
                )
            }
        }
    }

    // =========================
    // OFFLINE: inferensi lokal (assets), tidak simpan Firestore
    // =========================
    private suspend fun runOfflineFlow(rightUri: String, leftUri: String) {
        try {
            val detector = MLDetector.fromAssets(this) // Model lokal

            val resRight = withContext(Dispatchers.Default) {
                detector.classifyFromUri(this@DataPasienActivity, Uri.parse(rightUri))
            }
            val resLeft = withContext(Dispatchers.Default) {
                detector.classifyFromUri(this@DataPasienActivity, Uri.parse(leftUri))
            }

            val diagnosis = if (resRight.isPositive || resLeft.isPositive)
                "Terindikasi retinoblastoma (cek ke dokter)."
            else
                "Tidak terindikasi retinoblastoma."

            // Ke hasil (tanpa identitas)
            val move = Intent(this, HasilActivity::class.java).apply {
                putExtra("EXTRA_NAMA",    "Mode Offline")
                putExtra("EXTRA_NIK",     "")
                putExtra("EXTRA_TANGGAL", "")

                putExtra("RIGHT_EYE_URI", rightUri)
                putExtra("LEFT_EYE_URI",  leftUri)

                putExtra("RIGHT_LABEL",   resRight.label)
                putExtra("RIGHT_SCORE",   resRight.score)
                putExtra("LEFT_LABEL",    resLeft.label)
                putExtra("LEFT_SCORE",    resLeft.score)
                putExtra("DIAGNOSIS",     diagnosis)

                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            startActivity(move)
            finish()

        } catch (e: Exception) {
            Toast.makeText(this, "Gagal inferensi: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    // =========================
    // ONLINE: validasi -> inferensi remote -> simpan Firestore -> hasil
    // =========================
    private suspend fun runOnlineFlowSaveAndGo(
        nama: String,
        nik: String,
        tglMillis: Long,
        rightUri: String,
        leftUri: String
    ) {
        try {
            // 1) INFERENSI pakai Remote Model Firebase
            val detector = MLDetector.fromFirebase(this, "retino_model")

            val resRight = withContext(Dispatchers.Default) {
                detector.classifyFromUri(this@DataPasienActivity, Uri.parse(rightUri))
            }
            val resLeft = withContext(Dispatchers.Default) {
                detector.classifyFromUri(this@DataPasienActivity, Uri.parse(leftUri))
            }

            val diagnosis = if (resRight.isPositive || resLeft.isPositive)
                "Terindikasi retinoblastoma (cek ke dokter)."
            else
                "Tidak terindikasi retinoblastoma."

            // 2) SIMPAN ke Firestore (users/{uid}/pasien)
            auth.currentUser?.let { user ->
                val uid = user.uid
                val userRef = firestore.collection("users").document(uid)

                // Pastikan dokumen user ada
                val snap = userRef.get().await()
                if (!snap.exists()) {
                    userRef.set(
                        mapOf(
                            "nama" to (user.displayName ?: ""),
                            "email" to (user.email ?: "")
                        )
                    ).await()
                }

                val pasienData = hashMapOf(
                    "nama" to nama,
                    "nik" to nik,
                    "tanggal" to Timestamp(Date(tglMillis)),
                    "rightLabel" to resRight.label,
                    "rightScore" to resRight.score,
                    "leftLabel"  to resLeft.label,
                    "leftScore"  to resLeft.score,
                    "diagnosis"  to diagnosis,
                    "rightUri"   to rightUri, // opsional (menyimpan path foto)
                    "leftUri"    to leftUri,  // opsional
                    "createdAt"  to FieldValue.serverTimestamp()
                )
                userRef.collection("pasien").add(pasienData).await()
            } ?: run {
                Toast.makeText(this, "Harus login untuk menyimpan data.", Toast.LENGTH_SHORT).show()
            }

            // 3) BUKA HASIL
            val move = Intent(this, HasilActivity::class.java).apply {
                putExtra("EXTRA_NAMA",  nama)
                putExtra("EXTRA_NIK",   nik)
                putExtra("EXTRA_TANGGAL", sdfDisplay.format(Date(tglMillis)))

                putExtra("RIGHT_EYE_URI", rightUri)
                putExtra("LEFT_EYE_URI",  leftUri)

                putExtra("RIGHT_LABEL",   resRight.label)
                putExtra("RIGHT_SCORE",   resRight.score)
                putExtra("LEFT_LABEL",    resLeft.label)
                putExtra("LEFT_SCORE",    resLeft.score)
                putExtra("DIAGNOSIS",     diagnosis)
            }
            startActivity(move)
            finish()

        } catch (e: Exception) {
            Toast.makeText(this, "Gagal proses online: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // =========================
    // UI helpers (DatePicker + parser)
    // =========================
    private fun showTanggalPicker(target: TextInputEditText, til: TextInputLayout?) {
        val start1900 = Calendar.getInstance().apply {
            set(1900, Calendar.JANUARY, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0)
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
}
