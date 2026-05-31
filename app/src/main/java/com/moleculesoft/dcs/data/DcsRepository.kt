package com.moleculesoft.dcs.data

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.delay
import android.util.Log
import java.util.*

class DcsRepository {
    private val db by lazy { FirebaseFirestore.getInstance() }
    // Explicitly specify the bucket URL to ensure proper initialization
    private val storage by lazy { 
        FirebaseStorage.getInstance("gs://douala-community-sentinel.appspot.com")
    }
    private val auth by lazy { FirebaseAuth.getInstance() }

    private suspend fun ensureAuthenticated() {
        if (auth.currentUser == null) {
            try {
                auth.signInAnonymously().await()
            } catch (e: Exception) {
                Log.e("DcsRepository", "Anonymous sign-in failed. Please ensure 'Anonymous' provider is enabled in the Firebase Console (Authentication > Sign-in method).", e)
                throw e
            }
        }
    }

    suspend fun saveSensorData(data: SensorData): Boolean {
        return try {
            ensureAuthenticated()
            val dataWithUser = if (data.neighborhood == "Unknown") {
                // You could potentially add user-specific info here if needed
                data
            } else data
            db.collection("sensor_data").add(dataWithUser).await()
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
                finalReport = finalReport.copy(userId = auth.currentUser?.uid ?: "anonymous")
            }

            if (imageUri != null) {
                Log.d("DcsRepository", "Starting upload. User UID: ${auth.currentUser?.uid}, URI: $imageUri")
                val imagePath = "reports/${UUID.randomUUID()}.jpg"
                val ref = storage.reference.child(imagePath)
                
                try {
                    // Validate storage reference
                    if (ref == null) {
                        Log.e("DcsRepository", "Storage reference is null. Bucket may not be initialized.")
                        throw Exception("Storage reference initialization failed")
                    }
                    
                    Log.d("DcsRepository", "Uploading to path: $imagePath")
                    
                    // Start the upload with retry logic (max 3 attempts)
                    val uploadedUrl = uploadWithRetry(ref, imageUri, maxRetries = 3)
                    finalReport = finalReport.copy(imageUrl = uploadedUrl)
                    Log.d("DcsRepository", "Upload successful: $uploadedUrl")
                } catch (e: StorageException) {
                    Log.e("DcsRepository", "Firebase Storage Error: ${e.message}, Code: ${e.errorCode}", e)
                    // ERROR_NOT_AUTHORIZED (-13021) -> Check Security Rules in Firebase Console
                    // ERROR_RETRY_LIMIT_EXCEEDED (-13030) -> Network issues
                    // ERROR_OBJECT_NOT_FOUND (-13010) -> Bucket path invalid or not accessible
                    when (e.errorCode) {
                        StorageException.ERROR_NOT_AUTHORIZED -> {
                            Log.e("DcsRepository", "Authorization error. Check Firebase Storage security rules.")
                        }
                        StorageException.ERROR_OBJECT_NOT_FOUND -> {
                            Log.e("DcsRepository", "Storage bucket not found. Verify bucket URL: gs://douala-community-sentinel.appspot.com")
                        }
                    }
                    throw e
                } catch (e: Exception) {
                    Log.e("DcsRepository", "Unexpected error during image upload: ${e.message}", e)
                    throw e
                }
            }
            db.collection("reports").add(finalReport).await()
            true
        } catch (e: Exception) {
            Log.e("DcsRepository", "Error submitting report: ${e.message}", e)
            false
        }
    }
    
    private suspend fun uploadWithRetry(
        ref: com.google.firebase.storage.StorageReference,
        imageUri: Uri,
        maxRetries: Int = 3
    ): String {
        var lastException: Exception? = null
        var delayMs = 1000L // Start with 1 second delay
        
        for (attempt in 1..maxRetries) {
            try {
                Log.d("DcsRepository", "Upload attempt $attempt/$maxRetries for ${ref.path}")
                ref.putFile(imageUri).await()
                val downloadUrl = ref.downloadUrl.await()
                return downloadUrl.toString()
            } catch (e: Exception) {
                lastException = e
                Log.w("DcsRepository", "Upload attempt $attempt failed: ${e.message}", e)
                
                if (attempt < maxRetries) {
                    // Exponential backoff: 1s, 2s, 4s
                    Log.d("DcsRepository", "Retrying in ${delayMs}ms...")
                    try {
                        delay(delayMs)
                    } catch (delayE: Exception) {
                        Log.w("DcsRepository", "Delay interrupted", delayE)
                    }
                    delayMs *= 2
                }
            }
        }
        
        throw lastException ?: Exception("Upload failed after $maxRetries attempts")
    }
    
    suspend fun getRecentReports() : List<UrbanReport> {
        return try {
            db.collection("reports")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .await()
                .toObjects(UrbanReport::class.java)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
