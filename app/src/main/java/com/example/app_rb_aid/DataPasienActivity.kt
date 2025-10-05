package com.example.app_rb_aid

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import android.widget.ImageView

class DataPasienActivity : AppCompatActivity() {

    private val localeID = Locale("id", "ID")
    private val sdfDisplay = SimpleDateFormat("dd/MM/yyyy", localeID).apply { isLenient = false }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_pasien)
        findViewById<ImageView>(R.id.back_button_data_pasien).setOnClickListener { finish() }
        val etNama = findViewById<TextInputEditText>(R.id.PasienNama)
        val etNik  = findViewById<TextInputEditText>(R.id.PasienNIK)
        val etTgl  = findViewById<TextInputEditText>(R.id.PasienTanggal)
        val btnSimpan = findViewById<MaterialButton>(R.id.button_simpan)

        val tilNama: TextInputLayout? = findViewById(R.id.tilPasienNama)
        val tilNik : TextInputLayout? = findViewById(R.id.tilPasienNIK)
        val tilTgl : TextInputLayout? = findViewById(R.id.tilPasienTanggal)

        // Bersihkan error saat user mengedit
        etNama.doAfterTextChanged { tilNama?.error = null; etNama.error = null }
        etNik.doAfterTextChanged  { tilNik?.error  = null; etNik.error  = null }
        etTgl.doAfterTextChanged  { tilTgl?.error  = null; etTgl.error  = null }

        // Buka date picker
        etTgl.setOnClickListener { showTanggalPicker(etTgl, tilTgl) }
        tilTgl?.setEndIconOnClickListener { etTgl.performClick() }
        etTgl.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) etTgl.performClick() }

        btnSimpan.setOnClickListener {
            val nama = etNama.text?.toString()?.trim().orEmpty()
            val nik  = etNik.text?.toString()?.trim().orEmpty()
            val tgl  = etTgl.text?.toString()?.trim().orEmpty()

            val rightUri = intent.getStringExtra("RIGHT_EYE_URI")
            val leftUri  = intent.getStringExtra("LEFT_EYE_URI")

            var valid = true

            // Validasi nama
            if (nama.isEmpty()) {
                tilNama?.error = "Nama wajib diisi"
                if (valid) etNama.requestFocus()
                valid = false
            }

            // Validasi NIK
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

            // Validasi tanggal
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

            if (rightUri.isNullOrEmpty() || leftUri.isNullOrEmpty()) {
                Toast.makeText(this, "Foto mata kanan & kiri wajib diambil", Toast.LENGTH_SHORT).show()
                valid = false
            }

            if (!valid) return@setOnClickListener

            // ========= INFERENSI =========
            lifecycleScope.launch {
                try {
                    val detector = when (ModeManager.mode) {
                        ModeManager.Mode.OFFLINE -> MLDetector.fromAssets(this@DataPasienActivity)
                        ModeManager.Mode.ONLINE  -> {
                            // Ganti "retino_model" dengan nama remote model di Firebase Console
                            MLDetector.fromFirebase(this@DataPasienActivity, "retino_model")
                        }
                    }

                    val resRight = withContext(Dispatchers.Default) {
                        detector.classifyFromUri(this@DataPasienActivity, Uri.parse(rightUri!!))
                    }
                    val resLeft = withContext(Dispatchers.Default) {
                        detector.classifyFromUri(this@DataPasienActivity, Uri.parse(leftUri!!))
                    }

                    val diagnosis = when {
                        resRight.isPositive || resLeft.isPositive ->
                            "Terindikasi retinoblastoma (cek ke dokter)."
                        else -> "Tidak terindikasi retinoblastoma."
                    }

                    val intent = Intent(this@DataPasienActivity, HasilActivity::class.java).apply {
                        putExtra("EXTRA_NAMA", nama)
                        putExtra("EXTRA_NIK",  nik)
                        putExtra("EXTRA_TANGGAL", tgl)
                        putExtra("RIGHT_EYE_URI", rightUri)
                        putExtra("LEFT_EYE_URI",  leftUri)
                        putExtra("RIGHT_LABEL", resRight.label)
                        putExtra("RIGHT_SCORE", resRight.score)
                        putExtra("LEFT_LABEL",  resLeft.label)
                        putExtra("LEFT_SCORE",  resLeft.score)
                        putExtra("DIAGNOSIS",   diagnosis)
                    }
                    startActivity(intent)

                } catch (e: Exception) {
                    Toast.makeText(
                        this@DataPasienActivity,
                        "Gagal inferensi: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

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
