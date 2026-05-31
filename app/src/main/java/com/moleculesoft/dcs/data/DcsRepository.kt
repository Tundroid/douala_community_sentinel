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

    private suspend fun ensureAuthenticated() {
        if (auth.currentUserOrNull() == null) {
            try {
                auth.signInAnonymously()
            } catch (e: Exception) {
                Log.e("DcsRepository", "Supabase Anonymous sign-in failed. Ensure Anonymous auth is enabled in Supabase dashboard.", e)
                throw e
            }
        }
    }

    suspend fun saveSensorData(data: SensorData): Boolean {
        return try {
            ensureAuthenticated()
            db.from("sensor_data").insert(data)
            true
        } catch (e: Exception) {
            Log.e("DcsRepository", "Error saving sensor data", e)
            false
        }
    }

    suspend fun submitReport(report: UrbanReport, imageUri: Uri?): Boolean {
        return try {
            ensureAuthenticated()
            var finalReport = report
            
            // Assign the anonymous user ID if not already present
            if (finalReport.userId.isEmpty()) {
                finalReport = finalReport.copy(userId = auth.currentUserOrNull()?.id ?: "anonymous")
            }

            if (imageUri != null) {
                Log.d("DcsRepository", "Starting upload. User ID: ${auth.currentUserOrNull()?.id}, URI: $imageUri")
                val imagePath = "${UUID.randomUUID()}.jpg"
                val bucket = storage.from("reports")
                
                try {
                    Log.d("DcsRepository", "Uploading to path: $imagePath")
                    
                    // Start the upload with retry logic (max 3 attempts)
                    val uploadedUrl = uploadWithRetry(bucket, imagePath, imageUri, maxRetries = 3)
                    finalReport = finalReport.copy(imageUrl = uploadedUrl)
                    Log.d("DcsRepository", "Upload successful: $uploadedUrl")
                } catch (e: Exception) {
                    Log.e("DcsRepository", "Unexpected error during image upload: ${e.message}", e)
                    throw e
                }
            }
            db.from("reports").insert(finalReport)
            incrementUserPoints(10) // 10 points per report
            true
        } catch (e: Exception) {
            Log.e("DcsRepository", "Error submitting report: ${e.message}", e)
            false
        }
    }
    
    private suspend fun incrementUserPoints(points: Int) {
        val currentUserId = auth.currentUserOrNull()?.id ?: return
        try {
            // Fetch current points or default to 0
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
        imageUri: Uri,
        maxRetries: Int = 3
    ): String {
        var lastException: Exception? = null
        var delayMs = 1000L // Start with 1 second delay
        
        val bytes = DcsApplication.instance.contentResolver.openInputStream(imageUri)?.use { 
            it.readBytes() 
        } ?: throw Exception("Could not read image URI")

        for (attempt in 1..maxRetries) {
            try {
                Log.d("DcsRepository", "Upload attempt $attempt/$maxRetries for $imagePath")
                bucket.upload(imagePath, bytes) {
                    upsert = true
                }
                return bucket.publicUrl(imagePath)
            } catch (e: Exception) {
                lastException = e
                Log.w("DcsRepository", "Upload attempt $attempt failed: ${e.message}", e)
                
                if (attempt < maxRetries) {
                    // Exponential backoff
                    Log.d("DcsRepository", "Retrying in ${delayMs}ms...")
                    delay(delayMs)
                    delayMs *= 2
                }
            }
        }
        
        throw lastException ?: Exception("Upload failed after $maxRetries attempts")
    }
    
    suspend fun getRecentReports() : List<UrbanReport> {
        return try {
            db.from("reports")
                .select {
                    order("timestamp", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                    limit(50)
                }
                .decodeList<UrbanReport>()
        } catch (e: Exception) {
            Log.e("DcsRepository", "Error fetching reports", e)
            emptyList()
        }
    }
}
