package com.example.app_rb_aid

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.app_rb_aid.databinding.ActivityLoginBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.GoogleAuthProvider


@Suppress("DEPRECATION")
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    // Launcher untuk Google Sign-In
    private val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account: GoogleSignInAccount = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Toast.makeText(this, "Google Sign-In gagal: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    override fun onStart() {
        super.onStart()
        if (FirebaseAuth.getInstance().currentUser != null) {
            ModeManager.mode = ModeManager.Mode.ONLINE
            startActivity(Intent(this, BerandaActivity::class.java))
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityLoginBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // ======== NAVIGASI HALAMAN LAIN ========
        binding.btntxtBuatAkun.setOnClickListener {
            startActivity(Intent(this, BuatAkunActivity::class.java))
        }


        binding.btntxtLupaPassword.setOnClickListener {
            startActivity(Intent(this, ResetPasswordActivity::class.java))
        }

        // ======== GOOGLE SIGN-IN ========
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        binding.btnGoogleSignIn.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            launcher.launch(signInIntent)
        }

        // ======== MODE OFFLINE ========
        binding.btnOfflineMode.setOnClickListener {
            ModeManager.mode = ModeManager.Mode.OFFLINE
            Toast.makeText(this, "Mode OFFLINE aktif", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, BerandaActivity::class.java))
            finish() // supaya tidak kembali ke login
        }

        // ======== LOGIN EMAIL/PASSWORD (ONLINE) ========
        binding.btnMasuk.setOnClickListener {
            val email = binding.edtEmailMasuk.text.toString().trim()
            val password = binding.edtPasswordMasuk.text.toString()

            if (email.isEmpty()) {
                binding.edtEmailMasuk.error = "Email harus diisi"
                binding.edtEmailMasuk.requestFocus()
                return@setOnClickListener
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.edtEmailMasuk.error = "Email tidak valid"
                binding.edtEmailMasuk.requestFocus()
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                binding.edtPasswordMasuk.error = "Password harus diisi"
                binding.edtPasswordMasuk.requestFocus()
                return@setOnClickListener
            }

            loginFirebase(email, password)
        }
    }

    // ======== AUTH DENGAN GOOGLE ========
    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    ModeManager.mode = ModeManager.Mode.ONLINE
                    val user = auth.currentUser
                    Toast.makeText(this, "Selamat datang ${user?.displayName}", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, BerandaActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this, "Google Sign-In gagal", Toast.LENGTH_SHORT).show()
                }
            }
    }

    // ======== AUTH DENGAN EMAIL/PASSWORD ========
    private fun loginFirebase(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { result ->
                if (result.isSuccessful) {
                    ModeManager.mode = ModeManager.Mode.ONLINE
                    Toast.makeText(this, "Selamat datang $email", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, BerandaActivity::class.java))
                    finish()
                } else {
                    val ex = result.exception
                    when (ex) {
                        is FirebaseAuthInvalidUserException -> {
                            // Email belum terdaftar
                            Toast.makeText(this, "Email belum terdaftar", Toast.LENGTH_SHORT).show()
                            binding.edtEmailMasuk.error = "Email belum terdaftar"
                            binding.edtEmailMasuk.requestFocus()
                        }
                        is FirebaseAuthInvalidCredentialsException -> {
                            // Password salah
                            Toast.makeText(this, "Password salah", Toast.LENGTH_SHORT).show()
                            binding.edtPasswordMasuk.error = "Password salah"
                            binding.edtPasswordMasuk.requestFocus()
                        }
                        else -> {
                            Toast.makeText(this, ex?.message ?: "Terjadi kesalahan", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
    }
}
