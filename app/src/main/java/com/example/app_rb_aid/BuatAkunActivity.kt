package com.example.app_rb_aid

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.app_rb_aid.databinding.ActivityBuatAkunBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class BuatAkunActivity : AppCompatActivity() {

    lateinit var binding: ActivityBuatAkunBinding
    lateinit var auth : FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityBuatAkunBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        binding.btntxtMasuk.setOnClickListener{
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }

        binding.btnDaftar.setOnClickListener {
            val email = binding.edtEmailBuatAkun.text.toString()
            val nama = binding.edtNamaBuatAkun.text.toString()
            val password = binding.edtPasswordBuatAkun.text.toString()
            val konfPassword = binding.edtKonfirmasiPasswordBuatAkun.text.toString()

            if (email.isEmpty()){
                binding.edtEmailBuatAkun.error = "Email harus diisi"
                binding.edtEmailBuatAkun.requestFocus()
                return@setOnClickListener
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()){
                binding.edtEmailBuatAkun.error = "Email tidak valid"
                binding.edtEmailBuatAkun.requestFocus()
                return@setOnClickListener
            }

            if (password.isEmpty()){
                binding.edtPasswordBuatAkun.error = "Password harus diisi"
                binding.edtPasswordBuatAkun.requestFocus()
                return@setOnClickListener
            }

            if (password.length < 7){
                binding.edtPasswordBuatAkun.error = "Password minimal 7 karakter"
                binding.edtPasswordBuatAkun.requestFocus()
                return@setOnClickListener
            }

            if (konfPassword.isEmpty()) {
                binding.edtKonfirmasiPasswordBuatAkun.error = "Konfirmasi password tidak boleh kosong"
                binding.edtKonfirmasiPasswordBuatAkun.requestFocus()
                return@setOnClickListener
            }

            if (konfPassword != password) {
                binding.edtKonfirmasiPasswordBuatAkun.error = "Password tidak sama"
                binding.edtKonfirmasiPasswordBuatAkun.requestFocus()
                return@setOnClickListener
            }

            if (nama.isEmpty()){
                binding.edtNamaBuatAkun.error = "Nama harus diisi"
                binding.edtNamaBuatAkun.requestFocus()
                return@setOnClickListener
            }


            RegisterFirebase(email,password)
        }
    }

    private fun RegisterFirebase(email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Ambil UID dari user yang baru dibuat
                    val userId = auth.currentUser?.uid
                    val nama = binding.edtNamaBuatAkun.text.toString()

                    // Data yang mau disimpan
                    val userMap = hashMapOf(
                        "nama" to nama,
                        "email" to email
                    )

                    if (userId != null) {
                        val db = FirebaseFirestore.getInstance()
                        db.collection("users")
                            .document(userId)
                            .set(userMap)
                            .addOnSuccessListener {
                                Toast.makeText(this, "Buat Akun Berhasil", Toast.LENGTH_SHORT).show()
                                val intent = Intent(this, LoginActivity::class.java)
                                startActivity(intent)
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Gagal simpan data: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                } else {
                    Toast.makeText(this, "${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }
}