package com.example.app_rb_aid

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class HasilActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hasil)

        val tvName = findViewById<TextView>(R.id.tvName)
        val tvNik = findViewById<TextView>(R.id.tvNik)
        val tvTanggal = findViewById<TextView>(R.id.tvTanggal)
        val ivMataKanan = findViewById<ImageView>(R.id.ivMataKanan)
        val ivMataKiri = findViewById<ImageView>(R.id.ivMataKiri)
        val tvDiagnosis = findViewById<TextView>(R.id.tvDiagnosis)
        val btnDoctor = findViewById<ImageView>(R.id.btn_Doctor)
        val txtSimbol = findViewById<TextView>(R.id.txt_tekansimbol)

        val nama = intent.getStringExtra("EXTRA_NAMA") ?: "-"
        val nik = intent.getStringExtra("EXTRA_NIK") ?: "-"
        val tanggal = intent.getStringExtra("EXTRA_TANGGAL") ?: "-"
        val rightUri = intent.getStringExtra("RIGHT_EYE_URI")
        val leftUri  = intent.getStringExtra("LEFT_EYE_URI")

        val rLabel = intent.getStringExtra("RIGHT_LABEL") ?: "?"
        val rScore = intent.getFloatExtra("RIGHT_SCORE", -1f)
        val lLabel = intent.getStringExtra("LEFT_LABEL") ?: "?"
        val lScore = intent.getFloatExtra("LEFT_SCORE", -1f)

        val isOffline = ModeManager.mode == ModeManager.Mode.OFFLINE

        if (isOffline) {
            tvName.text = "Mode Offline"
            tvNik.text = ""
            tvTanggal.text = ""
            btnDoctor.visibility = View.GONE
            txtSimbol.visibility = View.GONE
        } else {
            tvName.text = nama
            tvNik.text = nik
            tvTanggal.text = tanggal
        }

        // tampilkan foto hasil
        rightUri?.let { ivMataKanan.setImageURI(Uri.parse(it)) }
        leftUri?.let  { ivMataKiri.setImageURI(Uri.parse(it)) }

        // tampilkan hasil klasifikasi
        tvDiagnosis.text = buildString {
            append("Mata kanan: $rLabel (p=${"%.2f".format(rScore)})\n")
            append("Mata kiri:  $lLabel (p=${"%.2f".format(lScore)})")
        }

        findViewById<ImageView>(R.id.back_button_data_pasien).setOnClickListener {
            finish()
        }

        btnDoctor.setOnClickListener {
            startActivity(Intent(this, HospitalListActivity::class.java))
        }
    }
}
