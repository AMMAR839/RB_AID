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
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.firestore.FirebaseFirestore

class HospitalListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: HospitalAdapter
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var userLat: Double = 0.0
    private var userLng: Double = 0.0

    companion object {
        private const val LOCATION_REQUEST_CODE = 1001
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
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    userLat = location.latitude
                    userLng = location.longitude
                    loadHospitals()
                } else {
                    Toast.makeText(this, "Lokasi user tidak ditemukan", Toast.LENGTH_SHORT).show()
                    loadHospitals()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal ambil lokasi: ${it.message}", Toast.LENGTH_SHORT).show()
            }
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
        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:${hospital.email}")
            putExtra(Intent.EXTRA_SUBJECT, "Permintaan Rujukan Dummy")
            putExtra(Intent.EXTRA_TEXT, "Halo ${hospital.name}, saya ingin meminta rujukan dummy.")
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
}