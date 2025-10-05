package com.example.app_rb_aid

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.util.Patterns
import android.widget.Toast
import com.example.app_rb_aid.databinding.ActivityResetPassBinding
import com.google.firebase.auth.FirebaseAuth

class ResetPasswordActivity : AppCompatActivity() {
    private lateinit var binding: ActivityResetPassBinding
    private lateinit var auth: FirebaseAuth
    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityResetPassBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // Tombol back
        binding.imgbtnBackGantiPass.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }

        // Tombol Reset Password
        binding.btnResetPassword.setOnClickListener {
            val email = binding.edtEmailGantiPass.text.toString().trim()

            if (email.isEmpty()) {
                binding.edtEmailGantiPass.error = "Email harus diisi"
                binding.edtEmailGantiPass.requestFocus()
                return@setOnClickListener
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.edtEmailGantiPass.error = "Format email tidak valid"
                binding.edtEmailGantiPass.requestFocus()
                return@setOnClickListener
            }

            // Kirim email reset password
            auth.sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(
                            this,
                            "Link reset password terkirim ke $email",
                            Toast.LENGTH_LONG
                        ).show()
                        finish() // kembali ke login
                    } else {
                        Toast.makeText(
                            this,
                            "Gagal: ${task.exception?.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        }
    }
}