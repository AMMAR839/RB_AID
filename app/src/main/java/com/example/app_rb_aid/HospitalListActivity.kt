package com.example.app_rb_aid

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.*
import com.google.android.gms.common.api.ResolvableApiException
import com.google.firebase.firestore.FirebaseFirestore

class HospitalListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: HospitalAdapter
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var userLat: Double = 0.0
    private var userLng: Double = 0.0

    companion object {
        private const val LOCATION_REQUEST_CODE = 1001
        private const val GPS_REQUEST_CODE = 2001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hospital_list)

        recyclerView = findViewById(R.id.recyclerView_hospital)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // cek permission dulu
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_REQUEST_CODE
            )
        } else {
            getUserLocation()
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun getUserLocation() {
        val locationRequest = LocationRequest.create().apply {
            priority = Priority.PRIORITY_HIGH_ACCURACY
            interval = 1000
            numUpdates = 1
        }

        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)

        val settingsClient = LocationServices.getSettingsClient(this)
        val task = settingsClient.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            // Lokasi sudah aktif → ambil lokasi
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        userLat = location.latitude
                        userLng = location.longitude
                        loadHospitals()
                    } else {
                        // kalau null, minta update lokasi baru
                        requestNewLocation(locationRequest)
                    }
                }
        }

        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                try {
                    // munculin dialog ke user buat nyalain GPS
                    exception.startResolutionForResult(this, GPS_REQUEST_CODE)
                } catch (sendEx: Exception) {
                    Toast.makeText(this, "Tidak bisa meminta lokasi", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    @Suppress("MissingPermission")
    private fun requestNewLocation(locationRequest: LocationRequest) {
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val location: Location? = result.lastLocation
                    if (location != null) {
                        userLat = location.latitude
                        userLng = location.longitude
                    }
                    fusedLocationClient.removeLocationUpdates(this)
                    loadHospitals()
                }
            },
            mainLooper
        )
    }

    private fun loadHospitals() {
        val db = FirebaseFirestore.getInstance()
        db.collection("hospitals")
            .get()
            .addOnSuccessListener { result ->
                val hospitalList = result.map { doc ->
                    val hospital = doc.toObject(Hospital::class.java)
                    val distance: Double = calculateDistance(userLat, userLng, hospital.latitude, hospital.longitude)
                    hospital.copy(distance = distance)
                }.sortedBy { it.distance }

                adapter = HospitalAdapter(
                    hospitalList,
                    onItemClick = { hospital -> openMaps(hospital) },
                    onSendEmailClick = { hospital -> sendEmail(hospital) }
                )
                recyclerView.adapter = adapter
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0].toDouble() / 1000 // km
    }

    private fun openMaps(hospital: Hospital) {
        val mapsUri = if (hospital.mapsUrl.isNotEmpty()) {
            Uri.parse(hospital.mapsUrl)
        } else {
            Uri.parse("https://www.google.com/maps/search/?api=1&query=${hospital.latitude},${hospital.longitude}")
        }

        val mapIntent = Intent(Intent.ACTION_VIEW, mapsUri)

        // coba pakai Google Maps dulu
        mapIntent.setPackage("com.google.android.apps.maps")

        // cek apakah ada aplikasi Maps
        if (mapIntent.resolveActivity(packageManager) != null) {
            startActivity(mapIntent)
        } else {
            // fallback ke browser
            val browserIntent = Intent(Intent.ACTION_VIEW, mapsUri)
            startActivity(browserIntent)
        }
    }

    private fun sendEmail(hospital: Hospital) {
        // --- Ambil data dari Intent yang dikirim HasilActivity ---
        val rightUri = intent.getStringExtra("RIGHT_EYE_URI")?.let { Uri.parse(it) }
        val leftUri  = intent.getStringExtra("LEFT_EYE_URI") ?.let { Uri.parse(it) }

        val rightLabel = intent.getStringExtra("RIGHT_LABEL")
        val leftLabel  = intent.getStringExtra("LEFT_LABEL")
        val rightScore = if (intent.hasExtra("RIGHT_SCORE")) intent.getFloatExtra("RIGHT_SCORE", -1f) else null
        val leftScore  = if (intent.hasExtra("LEFT_SCORE"))  intent.getFloatExtra("LEFT_SCORE",  -1f) else null
        val diagnosis  = intent.getStringExtra("DIAGNOSIS")

        val patientName = intent.getStringExtra("EXTRA_NAMA") ?: "-"
        val nik         = intent.getStringExtra("EXTRA_NIK")
        val dob         = intent.getStringExtra("EXTRA_TANGGAL_LAHIR")
        val contact     = intent.getStringExtra("EXTRA_KONTAK")
        val address     = intent.getStringExtra("EXTRA_ALAMAT")

        val examDate = intent.getStringExtra("EXTRA_TANGGAL_PEMERIKSAAN") ?: ""
        val examTime = intent.getStringExtra("EXTRA_WAKTU_PEMERIKSAAN") ?: ""

        if (hospital.email.isNullOrBlank()) {
            Toast.makeText(this, "Email rumah sakit belum tersedia", Toast.LENGTH_SHORT).show()
            return
        }

        // --- Template email ---
        val subject = com.example.app_rb_aid.util.EmailTemplates.subject(patientName, if (examDate.isNotBlank()) examDate else "Hari ini")
        val body = com.example.app_rb_aid.util.EmailTemplates.body(
            rsName = hospital.name ?: "Rumah Sakit",
            doctorOrTeam = "Dokter/Tim",
            patientName = patientName,
            nik = nik, dob = dob, contact = contact, address = address,
            examDate = if (examDate.isNotBlank()) examDate else "Hari ini",
            examTime = examTime,
            diagnosis = diagnosis,
            senderName = "Petugas/Orang Tua",
            appOrOrg = "RB-Aid"
        )

        // --- Lampiran (opsional)
        val attachments = arrayListOf<Uri>()
        rightUri?.let { attachments.add(it) }
        leftUri ?.let { attachments.add(it) }

        val emailIntent = if (attachments.size <= 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "message/rfc822"              // biar difilter ke email client
                putExtra(Intent.EXTRA_EMAIL, arrayOf(hospital.email))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                if (attachments.isNotEmpty()) putExtra(Intent.EXTRA_STREAM, attachments.first())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "message/rfc822"
                putExtra(Intent.EXTRA_EMAIL, arrayOf(hospital.email))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, attachments)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        startActivity(Intent.createChooser(emailIntent, "Kirim Email"))
    }


    // handle hasil request permission
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getUserLocation()
            } else {
                Toast.makeText(this, "Izin lokasi ditolak", Toast.LENGTH_SHORT).show()
                loadHospitals()
            }
        }
    }

    // handle hasil dari dialog GPS
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == GPS_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                // user sudah nyalain GPS → ulang ambil lokasi
                getUserLocation()
            } else {
                Toast.makeText(this, "Lokasi harus diaktifkan untuk menampilkan rumah sakit terdekat", Toast.LENGTH_LONG).show()
                loadHospitals()
            }
        }
    }
}