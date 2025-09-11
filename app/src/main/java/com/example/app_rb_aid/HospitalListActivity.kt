package com.example.app_rb_aid

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.recyclerview.widget.RecyclerView

class HospitalListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: HospitalAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hospital_list)

        recyclerView = findViewById(R.id.recyclerView_hospital)


        val dummyHospitals = listOf(
            Hospital("RSUD Jakarta", "2 KM", "rsudjakarta@gmail.com"),
            Hospital("RS Mata Bandung", "5 KM", "rsmata@gmail.com"),
            Hospital("RS Kanker Surabaya", "8 KM", "rskanker@gmail.com")
        )

        adapter = HospitalAdapter(dummyHospitals) { hospital ->
            showConfirmDialog(hospital)
        }

        recyclerView.adapter = adapter
    }

    private fun showConfirmDialog(hospital: Hospital) {
        AlertDialog.Builder(this)
            .setTitle("Konfirmasi Rujukan")
            .setMessage("Apakah Anda ingin mengirim email rujukan ke ${hospital.name}?")
            .setPositiveButton("Ya") { _, _ ->
                sendEmail(hospital)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun sendEmail(hospital: Hospital) {
        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = "mailto:${hospital.email}".toUri()
            putExtra(Intent.EXTRA_SUBJECT, "Permintaan Rujukan Dummy")
            putExtra(Intent.EXTRA_TEXT, "Halo ${hospital.name}, saya ingin meminta rujukan dummy.")
        }
        startActivity(Intent.createChooser(emailIntent, "Kirim Email"))
    }
}
