// HasilActivity.kt
package com.example.app_rb_aid

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import androidx.core.net.toUri

class HasilActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // extras yang kita butuhkan untuk upload
    private var rightUriStr: String? = null
    private var leftUriStr: String? = null
    private var rightLabel: String? = null
    private var rightScore: Float = -1f
    private var leftLabel: String? = null
    private var leftScore: Float = -1f

    private lateinit var loadingOverlay: View

    private fun isPositive(label: String?): Boolean =
        label?.equals("RB", true) == true || (label?.contains("retinoblastoma", true) == true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hasil)

        // ---------- Ambil extras ----------
        val nama = intent.getStringExtra("EXTRA_NAMA") ?: "-"
        val nik = intent.getStringExtra("EXTRA_NIK") ?: "-"
        val tanggal = intent.getStringExtra("EXTRA_TANGGAL") ?: "-"

        rightUriStr = intent.getStringExtra("RIGHT_EYE_URI")
        leftUriStr  = intent.getStringExtra("LEFT_EYE_URI")
        rightLabel  = intent.getStringExtra("RIGHT_LABEL")
        rightScore  = intent.getFloatExtra("RIGHT_SCORE", -1f)
        leftLabel   = intent.getStringExtra("LEFT_LABEL")
        leftScore   = intent.getFloatExtra("LEFT_SCORE", -1f)

        val diagnosis = buildDiagnosis(rightLabel, leftLabel)

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

        val hasRB = isPositive(rightLabel) || isPositive(leftLabel)
        ivStatus.setImageResource(if (hasRB) R.drawable.warn else R.drawable.icon_sehat)

        // ---------- ViewPager (per-mata) ----------
        val tabLayout = findViewById<TabLayout>(R.id.tabs)
        val viewPager = findViewById<ViewPager2>(R.id.viewPager)

        val pages = listOf(
            EyePage("Kanan", rightUriStr, rightLabel ?: "Unknown", rightScore),
            EyePage("Kiri",  leftUriStr,  leftLabel ?: "Unknown",  leftScore)
        )
        viewPager.adapter = HasilPagerAdapter(this, pages)
        TabLayoutMediator(tabLayout, viewPager) { tab, pos -> tab.text = pages[pos].title }.attach()

        // ---------- Tombol rujukan dokter ----------
        val btnDoctorContainer = findViewById<View>(R.id.btnDoctor)
        btnDoctorContainer.visibility = if (hasRB) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btn_Doctor).setOnClickListener {
            val go = Intent(this, HospitalListActivity::class.java).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                // URI foto (biar bisa jadi lampiran)
                putExtra("RIGHT_EYE_URI", rightUriStr)
                putExtra("LEFT_EYE_URI",  leftUriStr)

                // Hasil model online
                putExtra("RIGHT_LABEL", rightLabel)
                putExtra("RIGHT_SCORE", rightScore)
                putExtra("LEFT_LABEL",  leftLabel)
                putExtra("LEFT_SCORE",  leftScore)
                putExtra("DIAGNOSIS",   buildDiagnosis(rightLabel, leftLabel))

                // Data pasien & waktu (isi sesuai yang kamu punya)
                putExtra("EXTRA_NAMA", nama)
                putExtra("EXTRA_NIK",  nik)
                putExtra("EXTRA_TANGGAL_LAHIR", "")   // isi kalau ada
                putExtra("EXTRA_KONTAK", "")          // isi kalau ada
                putExtra("EXTRA_ALAMAT", "")          // isi kalau ada

                putExtra("EXTRA_TANGGAL_PEMERIKSAAN", tanggal) // atau tanggal sekarang
                putExtra("EXTRA_WAKTU_PEMERIKSAAN",   "")       // mis. "14:30 WIB"
            }
            startActivity(go)
        }


        // ---------- Back ----------
        findViewById<ImageView>(R.id.back_button_data_pasien).setOnClickListener { finish() }

        // ---------- Loading overlay ----------
        loadingOverlay = findViewById(R.id.loadingOverlay)

        // Kalau datang dari DataPasienActivity untuk online flow → lakukan upload & simpan di sini
        val needsUpload = intent.getBooleanExtra("NEEDS_UPLOAD", false)
        if (needsUpload && (rightUriStr != null || leftUriStr != null)) {
            showLoading(true)
            scope.launch { saveToFirebaseHere(nama, nik, tanggal) }
        }
    }

    private fun showLoading(show: Boolean) {
        loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
    }

    private suspend fun saveToFirebaseHere(nama: String, nik: String, tanggal: String) = withContext(Dispatchers.IO) {
        try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"
            val storage = FirebaseStorage.getInstance().reference
            val db = FirebaseFirestore.getInstance()

            val pasienId = "${nama.replace(" ", "_")}_${System.currentTimeMillis()}"
            val baseRef = storage.child("rb_images/$uid/$pasienId")

            var rightUrl: String? = null
            var leftUrl: String? = null

            rightUriStr?.let {
                val ref = baseRef.child("right_eye.jpg")
                rightUrl = ref.putFile(Uri.parse(it)).continueWithTask { ref.downloadUrl }.await().toString()
            }
            leftUriStr?.let {
                val ref = baseRef.child("left_eye.jpg")
                leftUrl = ref.putFile(it.toUri()).continueWithTask { ref.downloadUrl }.await().toString()
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

            withContext(Dispatchers.Main) {
                showLoading(false)
                Toast.makeText(this@HasilActivity, "Data tersimpan", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                showLoading(false)
                Toast.makeText(this@HasilActivity, "Gagal simpan: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun buildDiagnosis(rLabel: String?, lLabel: String?): String {
        fun pos(s: String?) = s?.equals("RB", true) == true || (s?.contains("retinoblastoma", true) == true)
        return if (pos(rLabel) || pos(lLabel)) "Terindikasi retinoblastoma (cek ke dokter)." else "Tidak terindikasi retinoblastoma."
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
