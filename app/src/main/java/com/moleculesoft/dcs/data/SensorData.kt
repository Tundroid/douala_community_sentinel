package com.moleculesoft.dcs.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Transient
import kotlinx.datetime.Instant

@Serializable
@Entity(tableName = "sensor_data")
data class SensorData(
    @Transient
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timestamp: Instant = Instant.fromEpochMilliseconds(java.lang.System.currentTimeMillis()),
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("accelerometer_variance")
    val accelerometerVariance: Double = 0.0,
    @SerialName("noise_level_db")
    val noiseLevelDb: Double = 0.0,
    val neighborhood: String = "Unknown",
    @Transient
    val pendingUpload: Boolean = true
)

@Serializable
@Entity(tableName = "urban_reports")
data class UrbanReport(
    @PrimaryKey
    val id: String = "",
    val timestamp: Instant = Instant.fromEpochMilliseconds(java.lang.System.currentTimeMillis()),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val type: String = "", // Flooding, Waste, Pothole, Noise
    val description: String = "",
    @SerialName("image_url")
    val imageUrl: String? = null,
    @SerialName("user_id")
    val userId: String = "", // Links to profiles.id (UUID string)
    val status: String = "pending",
    @Transient
    val pendingUpload: Boolean = true
)

@Serializable
@Entity(tableName = "profiles")
data class UserStats(
    @PrimaryKey
    val id: String = "",
    @SerialName("display_name")
    val displayName: String? = null,
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    val points: Long = 0,
    @SerialName("report_count")
    val reports: Int = 0
)
