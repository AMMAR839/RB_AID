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

    lateinit var binding: ActivityLoginBinding
    lateinit var auth : FirebaseAuth
    lateinit var googleSignInClient: GoogleSignInClient

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

        //fds
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        binding.btnGoogleSignIn.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            launcher.launch(signInIntent)
        }
        //fds

        binding.btntxtGantiPassword.setOnClickListener {
            val intent = Intent(this, GantiPassActivity::class.java)
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

            loginFirebase(email,password)
        }


    }

    // 3. ActivityResultLauncher untuk Google Sign In
    private val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){ result ->
        if (result.resultCode == RESULT_OK){
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account: GoogleSignInAccount = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException){
                Toast.makeText(this, "Google Sign In Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 4. Login dengan Firebase pakai credential Google
    private fun firebaseAuthWithGoogle(idToken: String){
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this){ task ->
                if (task.isSuccessful){
                    val user = auth.currentUser
                    Toast.makeText(this, "Selamat Datang ${user?.displayName}", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, BerandaActivity::class.java)
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "Google Sign In gagal", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun loginFirebase(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this){
                if (it.isSuccessful){
                    Toast.makeText(this, "Selamat Datang $email", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, BerandaActivity::class.java)
                    startActivity(intent)
                } else{
                    try {
                        throw it.exception!!
                    } catch (e: FirebaseAuthInvalidCredentialsException) {
                        // Salah password
                        Toast.makeText(this, "Password salah", Toast.LENGTH_SHORT).show()
                        binding.edtPasswordMasuk.error = "Password salah"
                        binding.edtPasswordMasuk.requestFocus()
                    } catch (e: FirebaseAuthInvalidUserException) {
                        // Email tidak terdaftar
                        Toast.makeText(this, "Email belum terdaftar", Toast.LENGTH_SHORT).show()
                        binding.edtEmailMasuk.error = "Email belum terdaftar"
                        binding.edtEmailMasuk.requestFocus()
                    } catch (e: Exception) {
                        // Error umum lain
                        Toast.makeText(this, "${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }

    }
}