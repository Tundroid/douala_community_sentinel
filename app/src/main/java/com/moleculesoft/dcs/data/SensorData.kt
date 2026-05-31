package com.moleculesoft.dcs.data

import com.google.firebase.Timestamp

data class SensorData(
    val timestamp: Timestamp = Timestamp.now(),
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accelerometerVariance: Double = 0.0,
    val noiseLevelDb: Double = 0.0,
    val neighborhood: String = "Unknown"
)

data class UrbanReport(
    val id: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val type: String = "", // Flooding, Waste, Pothole, Noise
    val description: String = "",
    val imageUrl: String? = null,
    val userId: String = ""
)
