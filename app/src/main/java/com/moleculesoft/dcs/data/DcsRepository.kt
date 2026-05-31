package com.moleculesoft.dcs.data

import android.net.Uri
import android.util.Log
import com.moleculesoft.dcs.DcsApplication
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.BucketApi
import java.util.*

class DcsRepository {
    private val supabase = DcsApplication.supabase
    private val db = supabase.postgrest
    private val storage = supabase.storage
    private val auth = supabase.auth
    private val localDb = DcsApplication.database
    private val sensorDao = localDb.sensorDao()
    private val reportDao = localDb.reportDao()

    private suspend fun ensureAuthenticated() {
        if (auth.currentUserOrNull() == null) {
            try {
                // We manually load because autoLoadFromStorage is set to false in DcsApplication
                // to avoid startup crashes. We catch Throwable here because SettingsSessionManager
                // may throw IllegalStateException if the key is missing in some versions.
                auth.loadFromStorage()
            } catch (e: Throwable) {
                Log.e("DcsRepository", "Failed to load saved session", e)
            }
        }

        if (auth.currentUserOrNull() == null) {
            try {
                auth.signInAnonymously()
            } catch (e: Throwable) {
                Log.e("DcsRepository", "Supabase Anonymous sign-in failed.", e)
            }
        }
    }

    suspend fun saveSensorData(data: SensorData): Boolean {
        val record = data.copy(pendingUpload = true)
        val rowId = sensorDao.insertSensorData(record)

        return try {
            ensureAuthenticated()
            db.from("sensor_data").insert(record)
            if (rowId != -1L) {
                sensorDao.setSensorDataUploaded(rowId.toInt(), false)
            }
            true
        } catch (e: Exception) {
            Log.e("DcsRepository", "Error saving sensor data to cloud", e)
            false
        }
    }

    suspend fun syncPendingSensorData(): Boolean {
        val pendingData = sensorDao.getPendingSensorData()
        if (pendingData.isEmpty()) return true

        ensureAuthenticated()
        var allSuccess = true

        pendingData.forEach { pending ->
            try {
                db.from("sensor_data").insert(pending)
                sensorDao.setSensorDataUploaded(pending.id, false)
            } catch (e: Exception) {
                Log.e("DcsRepository", "Failed to sync sensor data item ${pending.id}", e)
                allSuccess = false
            }
        }

        return allSuccess
    }

    suspend fun submitReport(report: UrbanReport, imageUri: Uri?): Boolean {
        val localReport = report.copy(pendingUpload = true)
        reportDao.insertReport(localReport)

        return try {
            ensureAuthenticated()
            var finalReport = localReport

            if (finalReport.userId.isEmpty()) {
                finalReport = finalReport.copy(userId = auth.currentUserOrNull()?.id ?: "anonymous")
            }

            if (imageUri != null) {
                val imagePath = "${UUID.randomUUID()}.jpg"
                val bucket = storage.from("reports")
                val uploadedUrl = uploadWithRetry(bucket, imagePath, imageUri)
                finalReport = finalReport.copy(imageUrl = uploadedUrl)
                Log.d("DcsRepository", "Image uploaded to: $uploadedUrl")
            }

            db.from("reports").insert(finalReport)
            reportDao.setReportUploaded(finalReport.id, false, finalReport.imageUrl)
            incrementUserPoints(10)
            true
        } catch (e: Exception) {
            Log.e("DcsRepository", "Error submitting report to cloud", e)
            false
        }
    }

    suspend fun syncPendingReports(): Boolean {
        val pendingReports = reportDao.getPendingReports()
        if (pendingReports.isEmpty()) return true

        ensureAuthenticated()
        var allSuccess = true

        pendingReports.forEach { pending ->
            try {
                db.from("reports").insert(pending)
                reportDao.setReportUploaded(pending.id, false, pending.imageUrl)
            } catch (e: Exception) {
                Log.e("DcsRepository", "Failed to sync report ${pending.id}", e)
                allSuccess = false
            }
        }

        return allSuccess
    }

    suspend fun syncAllPendingData(): Boolean {
        val sensorSuccess = syncPendingSensorData()
        val reportsSuccess = syncPendingReports()
        return sensorSuccess && reportsSuccess
    }

    suspend fun getLatestSensorData(): SensorData? {
        return sensorDao.getRecentSensorData().firstOrNull()
    }

    suspend fun getPendingSensorCount(): Int {
        return sensorDao.getPendingSensorData().size
    }

    suspend fun getPendingReportCount(): Int {
        return reportDao.getPendingReports().size
    }

    suspend fun getLocalReports(): List<UrbanReport> {
        return reportDao.getAllReports()
    }

    private suspend fun incrementUserPoints(points: Int) {
        val currentUserId = auth.currentUserOrNull()?.id ?: return
        try {
            val response = db.from("profiles").select {
                filter { eq("id", currentUserId) }
            }.decodeSingleOrNull<UserStats>()

            val newPoints = (response?.points ?: 0L) + points
            val newReports = (response?.reports ?: 0) + 1
            db.from("profiles").upsert(UserStats(id = currentUserId, points = newPoints, reports = newReports))
        } catch (e: Exception) {
            Log.e("DcsRepository", "Error incrementing points", e)
        }
    }

    suspend fun getUserStats(): UserStats {
        val currentUserId = auth.currentUserOrNull()?.id ?: return UserStats()
        return try {
            db.from("profiles").select {
                filter { eq("id", currentUserId) }
            }.decodeSingleOrNull<UserStats>() ?: UserStats(id = currentUserId)
        } catch (e: Exception) {
            UserStats()
        }
    }

    private suspend fun uploadWithRetry(
        bucket: BucketApi,
        imagePath: String,
        imageUri: Uri
    ): String {
        val bytes = DcsApplication.instance.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
            ?: throw Exception("Could not read image")

        bucket.upload(imagePath, bytes) { upsert = true }
        return bucket.publicUrl(imagePath)
    }

    suspend fun getRecentReports(): List<UrbanReport> {
        return try {
            val reports = db.from("reports")
                .select {
                    order("timestamp", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                    limit(50)
                }
                .decodeList<UrbanReport>()

            reports.forEach { reportDao.insertReport(it.copy(pendingUpload = false)) }
            reports.map { it.copy(pendingUpload = false) }
        } catch (e: Exception) {
            Log.e("DcsRepository", "Cloud fetch failed, using local data", e)
            reportDao.getAllReports()
        }
    }
}
