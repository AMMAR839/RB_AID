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