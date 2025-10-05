package com.example.app_rb_aid

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.ui.AppBarConfiguration
import com.example.app_rb_aid.databinding.ActivityGantiNamaBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import android.widget.Toast

class GantiNamaActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityGantiNamaBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityGantiNamaBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)

        // Tampilkan nama saat ini
        val user = FirebaseAuth.getInstance().currentUser
        binding.txtNamaSekarang.text = user?.displayName ?: "Belum ada nama"

        // Tombol kembali
        binding.imgbtnBackGantiNama.setOnClickListener {
            val intent = Intent(this, BerandaActivity::class.java)
            startActivity(intent)
            finish()
        }

        // Tombol Ganti Nama
        binding.btnGantiNama.setOnClickListener {
            val newName = binding.edtNamaBaru.text.toString().trim()

            if (newName.isEmpty()) {
                binding.edtNamaBaru.error = "Nama tidak boleh kosong"
                binding.edtNamaBaru.requestFocus()
                return@setOnClickListener
            }

            val user = FirebaseAuth.getInstance().currentUser
            if (user != null) {
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(newName)
                    .build()

                user.updateProfile(profileUpdates)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val userId = user.uid
                            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()

                            db.collection("users")
                                .document(userId)
                                .update("nama", newName)
                                .addOnSuccessListener {
                                    Toast.makeText(this, "Nama berhasil diubah", Toast.LENGTH_SHORT).show()
                                    binding.txtNamaSekarang.text = newName // update tampilan langsung
                                    finish()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(this, "Gagal update Firestore: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        } else {
                            Toast.makeText(this, "Gagal mengubah nama: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                        }
                    }

            }
        }
    }
}