package com.example.app_rb_aid

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class HasilActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hasil)

        val tvName = findViewById<TextView>(R.id.tvName)
        val tvNik = findViewById<TextView>(R.id.tvNik)
        val ivMataKanan = findViewById<ImageView>(R.id.ivMataKanan)
        val ivMataKiri = findViewById<ImageView>(R.id.ivMataKiri)
        val tvDiagnosis = findViewById<TextView>(R.id.tvDiagnosis)

        // Set data statis (dummy)
        tvName.text = "Jane Doe"
        tvNik.text = "321651413121432"
        ivMataKanan.setImageResource(R.drawable.sehat_1)
        ivMataKiri.setImageResource(R.drawable.sehat_2)
        tvDiagnosis.text = "Tidak terindikasi terkena retinoblastoma"

        // tombol back
        findViewById<ImageView>(R.id.back_button_data_pasien).setOnClickListener {
            finish()
        }
    }
}
