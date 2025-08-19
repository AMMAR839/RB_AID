package com.example.app_rb_aid

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.app_rb_aid.databinding.ActivityBuatAkunBinding
import com.google.firebase.auth.FirebaseAuth

class BuatAkunActivity : AppCompatActivity() {

    lateinit var binding: ActivityBuatAkunBinding
    lateinit var auth : FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityBuatAkunBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        binding.btnDaftar.setOnClickListener {
            val email = binding.edtNamaBuatAkun.text.toString()
            val nama = binding.edtNamaBuatAkun.text.toString()
            val password = binding.edtPasswordBuatAkun.text.toString()

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
            .addOnCompleteListener(this){
                if (it.isSuccessful){
                    Toast.makeText(this, "Buat Akun Berhasil", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, LoginActivity::class.java)
                    startActivity(intent)
                } else{
                    Toast.makeText(this, "${it.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }

    }
}