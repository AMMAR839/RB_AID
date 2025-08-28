package com.example.app_rb_aid

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.app_rb_aid.databinding.ActivityLoginBinding
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    lateinit var binding: ActivityLoginBinding
    lateinit var auth : FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityLoginBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        binding.btntxtBuatAkun.setOnClickListener {
            val intent = Intent(this, BuatAkunActivity::class.java)
            startActivity(intent)
        }

        binding.btnMasuk.setOnClickListener{
            val email = binding.edtEmailMasuk.text.toString()
            val password = binding.edtPasswordMasuk.text.toString()

            if (email.isEmpty()){
                binding.edtEmailMasuk.error = "Email harus diisi"
                binding.edtEmailMasuk.requestFocus()
                return@setOnClickListener
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()){
                binding.edtEmailMasuk.error = "Email tidak valid"
                binding.edtEmailMasuk.requestFocus()
                return@setOnClickListener
            }

            if (password.isEmpty()){
                binding.edtPasswordMasuk.error = "Password harus diisi"
                binding.edtPasswordMasuk.requestFocus()
                return@setOnClickListener
            }

            LoginFirebase(email,password)
        }
    }

    private fun LoginFirebase(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this){
                if (it.isSuccessful){
                    Toast.makeText(this, "Selamat Datang$email", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, BerandaActivity::class.java)
                    startActivity(intent)
                } else{
                    Toast.makeText(this, "${it.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }

    }
}