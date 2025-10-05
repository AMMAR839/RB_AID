package com.example.app_rb_aid

data class Hospital(
    val name: String = "",
    val email: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val mapsUrl: String = "",
    val address: String = "",
    val distance: Double = 0.0    // tambahan
)

