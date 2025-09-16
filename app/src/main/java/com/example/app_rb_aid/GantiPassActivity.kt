package com.example.app_rb_aid

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.content.Intent
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import com.example.app_rb_aid.databinding.ActivityGantiPassBinding
import com.google.firebase.Firebase
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth

class GantiPassActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGantiPassBinding
    private lateinit var auth: FirebaseAuth
    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityGantiPassBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        binding.btnGantiPassword.setOnClickListener {
            val current = binding.edtPasswordLama.text.toString().trim()
            val newPass = binding.edtPasswordBaru.text.toString().trim()
            val confirm = binding.edtKonfirmasiPassword.text.toString().trim()

            // Validasi input
            if (current.isEmpty()) {
                binding.edtPasswordLama.error = "Password sekarang harus diisi"
                binding.edtPasswordLama.requestFocus()
                return@setOnClickListener
            }
            if (newPass.length < 7) {
                binding.edtPasswordBaru.error = "Password minimal 7 karakter"
                binding.edtPasswordBaru.requestFocus()
                return@setOnClickListener
            }
            if (confirm != newPass) {
                binding.edtKonfirmasiPassword.error = "Konfirmasi tidak sama"
                binding.edtKonfirmasiPassword.requestFocus()
                return@setOnClickListener
            }

            val user = auth.currentUser
            if (user == null) {
                Toast.makeText(this, "User tidak ditemukan. Silakan login ulang.", Toast.LENGTH_SHORT).show()
                // redirect ke login
                val i = Intent(this, LoginActivity::class.java)
                i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(i)
                finish()
                return@setOnClickListener
            }

            val email = user.email
            if (email.isNullOrEmpty()) {
                Toast.makeText(this, "Email tidak tersedia. Tidak bisa ganti password.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Re-authenticate dengan credential email + current password
            val credential = EmailAuthProvider.getCredential(email, current)
            user.reauthenticate(credential).addOnCompleteListener { reauthTask ->
                if (reauthTask.isSuccessful) {
                    // Setelah reauth sukses, update password
                    user.updatePassword(newPass).addOnCompleteListener { updateTask ->
                        if (updateTask.isSuccessful) {
                            Toast.makeText(this, "Password berhasil diubah. Silakan login ulang.", Toast.LENGTH_LONG).show()
                            // sign out supaya user login ulang dengan password baru
                            auth.signOut()
                            val i = Intent(this, LoginActivity::class.java)
                            i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(i)
                            finish()
                        } else {
                            Toast.makeText(this, "Gagal mengubah password: ${updateTask.exception?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    // Reauth gagal — kemungkinan password lama salah
                    Toast.makeText(this, "Re-authentication gagal: ${reauthTask.exception?.message}", Toast.LENGTH_SHORT).show()
                    binding.edtPasswordLama.error = "Password sekarang salah"
                    binding.edtPasswordLama.requestFocus()
                }
            }
        }
    }
}