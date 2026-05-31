package com.moleculesoft.dcs.data

import android.net.Uri
import android.util.Log
import com.moleculesoft.dcs.DcsApplication
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.delay
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
                auth.signInAnonymously()
            } catch (e: Exception) {
                Log.e("DcsRepository", "Supabase Anonymous sign-in failed.", e)
            }
        }
    }

    suspend fun saveSensorData(data: SensorData): Boolean {
        // Local first
        sensorDao.insertSensorData(data)
        
        return try {
            ensureAuthenticated()
            db.from("sensor_data").insert(data)
            true
        } catch (e: Exception) {
            Log.e("DcsRepository", "Error saving sensor data to cloud", e)
            false
        }
    }

    suspend fun submitReport(report: UrbanReport, imageUri: Uri?): Boolean {
        // Save to local Room first
        reportDao.insertReport(report)
        
        return try {
            ensureAuthenticated()
            var finalReport = report
            
            if (finalReport.userId.isEmpty()) {
                finalReport = finalReport.copy(userId = auth.currentUserOrNull()?.id ?: "anonymous")
            }

            if (imageUri != null) {
                val imagePath = "${UUID.randomUUID()}.jpg"
                val bucket = storage.from("reports")
                val uploadedUrl = uploadWithRetry(bucket, imagePath, imageUri)
                finalReport = finalReport.copy(imageUrl = uploadedUrl)
            }
            db.from("reports").insert(finalReport)
            incrementUserPoints(10)
            true
        } catch (e: Exception) {
            Log.e("DcsRepository", "Error submitting report to cloud", e)
            false
        }
    }
    
    private suspend fun incrementUserPoints(points: Int) {
        val currentUserId = auth.currentUserOrNull()?.id ?: return
        try {
            val response = db.from("users").select {
                filter { eq("id", currentUserId) }
            }.decodeSingleOrNull<UserStats>()
            
            val newPoints = (response?.points ?: 0L) + points
            db.from("users").upsert(UserStats(id = currentUserId, points = newPoints))
        } catch (e: Exception) {
            Log.e("DcsRepository", "Error incrementing points", e)
        }
    }

    suspend fun getUserStats(): UserStats {
        val currentUserId = auth.currentUserOrNull()?.id ?: return UserStats()
        return try {
            db.from("users").select {
                filter { eq("id", currentUserId) }
            }.decodeSingleOrNull<UserStats>() ?: UserStats(id = currentUserId)
        } catch (e: Exception) {
            UserStats()
        }
    }

    private suspend fun uploadWithRetry(
        bucket: io.github.jan.supabase.storage.BucketApi,
        imagePath: String,
        imageUri: Uri
    ): String {
        val bytes = DcsApplication.instance.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
            ?: throw Exception("Could not read image")
        
        bucket.upload(imagePath, bytes) { upsert = true }
        return bucket.publicUrl(imagePath)
    }
    
    suspend fun getRecentReports() : List<UrbanReport> {
        // Try cloud first, fallback to local
        return try {
            val reports = db.from("reports")
                .select {
                    order("timestamp", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                    limit(50)
                }
                .decodeList<UrbanReport>()
            
            // Update local cache
            reports.forEach { reportDao.insertReport(it) }
            reports
        } catch (e: Exception) {
            Log.e("DcsRepository", "Cloud fetch failed, using local data", e)
            reportDao.getAllReports()
        }
    }
}
