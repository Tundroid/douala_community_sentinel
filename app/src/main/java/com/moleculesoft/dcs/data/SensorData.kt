package com.moleculesoft.dcs.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlin.time.Clock
import kotlin.time.Instant

@Serializable
data class SensorData(
    val timestamp: Instant = Clock.System.now(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("accelerometer_variance")
    val accelerometerVariance: Double = 0.0,
    @SerialName("noise_level_db")
    val noiseLevelDb: Double = 0.0,
    val neighborhood: String = "Unknown"
)

@Serializable
data class UrbanReport(
    val id: String? = null,
    val timestamp: Instant = Clock.System.now(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val type: String = "", // Flooding, Waste, Pothole, Noise
    val description: String = "",
    @SerialName("image_url")
    val imageUrl: String? = null,
    @SerialName("user_id")
    val userId: String = ""
)
