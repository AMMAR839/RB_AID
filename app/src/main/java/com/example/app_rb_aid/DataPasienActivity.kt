package com.example.app_rb_aid

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.button.MaterialButton

class DataPasienActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_pasien) // ganti dengan nama layout yang sesuai

        val etNama = findViewById<TextInputEditText>(R.id.PasienNama)
        val etNik = findViewById<TextInputEditText>(R.id.PasienNIK)
        val btnSimpan = findViewById<MaterialButton>(R.id.button_simpan)

        btnSimpan.setOnClickListener {
            val nama = etNama.text.toString().trim()
            val nik = etNik.text.toString().trim()

            val intent = Intent(this, HasilActivity::class.java)
            intent.putExtra("EXTRA_NAMA", nama)
            intent.putExtra("EXTRA_NIK", nik)
            intent.putExtra("RIGHT_EYE_URI", getIntent().getStringExtra("RIGHT_EYE_URI"))
            intent.putExtra("LEFT_EYE_URI", getIntent().getStringExtra("LEFT_EYE_URI"))
            startActivity(intent)
        }
    }
}
