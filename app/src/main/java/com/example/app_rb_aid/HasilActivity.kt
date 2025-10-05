package com.example.app_rb_aid

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class HasilActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hasil)

        // ---------- Ambil extras ----------
        val nama = intent.getStringExtra("EXTRA_NAMA") ?: "-"
        val nik = intent.getStringExtra("EXTRA_NIK") ?: "-"

        val rightUriStr = intent.getStringExtra("RIGHT_EYE_URI")
        val leftUriStr  = intent.getStringExtra("LEFT_EYE_URI")

        val diagnosis   = intent.getStringExtra("DIAGNOSIS") ?: "-"

        val rLabel = intent.getStringExtra("RIGHT_LABEL") ?: "?"
        val rScore = intent.getFloatExtra("RIGHT_SCORE", -1f)
        val lLabel = intent.getStringExtra("LEFT_LABEL") ?: "?"
        val lScore = intent.getFloatExtra("LEFT_SCORE", -1f)

        // ---------- Header / ringkasan ----------
        val ivStatus = findViewById<ImageView>(R.id.ivStatus)
        val tvTitle  = findViewById<TextView>(R.id.tvTitle)
        val tvName   = findViewById<TextView>(R.id.tvName)
        val tvNik    = findViewById<TextView>(R.id.tvNik)
        val tvDiag   = findViewById<TextView>(R.id.tvDiagnosis)

        tvTitle.text = "HASIL"
        tvName.text  = nama
        tvNik.text   = nik
        tvDiag.text  = diagnosis

        val hasRB = rLabel.equals("RB", true) || lLabel.equals("RB", true)
        ivStatus.setImageResource(if (hasRB) R.drawable.warn else R.drawable.icon_sehat)

        // ---------- ViewPager (per-mata) ----------
        val tabLayout = findViewById<TabLayout>(R.id.tabs)
        val viewPager = findViewById<ViewPager2>(R.id.viewPager)

        val pages = listOf(
            EyePage(
                title = "Kanan",
                imageUri = rightUriStr,
                label = rLabel,
                score = rScore
            ),
            EyePage(
                title = "Kiri",
                imageUri = leftUriStr,
                label = lLabel,
                score = lScore
            )
        )

        viewPager.adapter = HasilPagerAdapter(this, pages)

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = pages[position].title
        }.attach()

        // ---------- Tombol rujukan dokter (muncul hanya jika ada RB) ----------
        val btnDoctorContainer = findViewById<View>(R.id.btnDoctor)
        btnDoctorContainer.visibility = if (hasRB) View.VISIBLE else View.GONE

        findViewById<View>(R.id.btn_Doctor).setOnClickListener {
            startActivity(Intent(this, HospitalListActivity::class.java))
        }

        // ---------- Back ----------
        findViewById<ImageView>(R.id.back_button_data_pasien).setOnClickListener {
            finish()
        }
    }
}
