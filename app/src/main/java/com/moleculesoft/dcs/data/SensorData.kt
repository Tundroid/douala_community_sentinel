package com.moleculesoft.dcs.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.datetime.Instant

@Serializable
@Entity(tableName = "sensor_data")
data class SensorData(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timestamp: Instant = kotlinx.datetime.Clock.System.now(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("accelerometer_variance")
    val accelerometerVariance: Double = 0.0,
    @SerialName("noise_level_db")
    val noiseLevelDb: Double = 0.0,
    val neighborhood: String = "Unknown"
)

@Serializable
@Entity(tableName = "urban_reports")
data class UrbanReport(
    @PrimaryKey
    val id: String = "",
    val timestamp: Instant = kotlinx.datetime.Clock.System.now(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val type: String = "", // Flooding, Waste, Pothole, Noise
    val description: String = "",
    @SerialName("image_url")
    val imageUrl: String? = null,
    @SerialName("user_id")
    val userId: String = ""
)

@Serializable
@Entity(tableName = "user_stats")
data class UserStats(
    @PrimaryKey
    val id: String = "",
    val points: Long = 0,
    val reports: Int = 0
)
