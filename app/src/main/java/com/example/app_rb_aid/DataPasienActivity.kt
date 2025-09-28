package com.example.app_rb_aid

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.app_rb_aid.databinding.ActivityDataPasienBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class DataPasienActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDataPasienBinding
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var selectedDate: Date? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDataPasienBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // === Calendar button click ===
        binding.imgbtnCalendarPasien.setOnClickListener {
            showDatePicker()
        }

        // === Save button click ===
        binding.buttonSimpan.setOnClickListener {
            saveData()
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePicker = DatePickerDialog(
            this,
            { _, selectedYear, selectedMonth, selectedDay ->
                calendar.set(selectedYear, selectedMonth, selectedDay)
                selectedDate = calendar.time
                val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                binding.txtTanggalLahirPasien.text = formatter.format(selectedDate!!)
            },
            year, month, day
        )
        datePicker.show()
    }

    private fun saveData() {
        val nama = binding.PasienNama.text.toString().trim()
        val nik = binding.PasienNIK.text.toString().trim()
        val tanggal = selectedDate

        if (nama.isEmpty() || nik.isEmpty() || tanggal == null) {
            binding.txtTanggalLahirPasien.error = "Lengkapi semua data"
            return
        }

        val userId = auth.currentUser?.uid ?: return

        val pasienData = hashMapOf(
            "nama" to nama,
            "nik" to nik,
            "tanggal" to tanggal
        )

        val userRef = firestore.collection("users").document(userId)

        userRef.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                userRef.set(
                    mapOf(
                        "nama" to auth.currentUser?.displayName,
                        "email" to auth.currentUser?.email
                    )
                )
            }

            userRef.collection("pasien")
                .add(pasienData)
                .addOnSuccessListener {
                    val intent = Intent(this, HasilActivity::class.java)
                    intent.putExtra("EXTRA_NAMA", nama)
                    intent.putExtra("EXTRA_NIK", nik)
                    intent.putExtra("EXTRA_TANGGAL", tanggal.time)
                    intent.putExtra("RIGHT_EYE_URI", intent.getStringExtra("RIGHT_EYE_URI"))
                    intent.putExtra("LEFT_EYE_URI", intent.getStringExtra("LEFT_EYE_URI"))
                    startActivity(intent)
                    finish()
                }
        }
    }
}
