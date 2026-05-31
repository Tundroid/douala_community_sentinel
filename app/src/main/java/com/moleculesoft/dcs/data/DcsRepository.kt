package com.moleculesoft.dcs.data

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import kotlinx.coroutines.tasks.await
import android.util.Log
import java.util.*

class DcsRepository {
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val storage by lazy { FirebaseStorage.getInstance() }
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
                    // Start the upload
                    ref.putFile(imageUri).await()
                    val downloadUrl = ref.downloadUrl.await()
                    finalReport = finalReport.copy(imageUrl = downloadUrl.toString())
                    Log.d("DcsRepository", "Upload successful: $downloadUrl")
                } catch (e: StorageException) {
                    Log.e("DcsRepository", "Firebase Storage Error: ${e.message}, Code: ${e.errorCode}", e)
                    // ERROR_NOT_AUTHORIZED (-13021) -> Check Security Rules in Firebase Console
                    // ERROR_RETRY_LIMIT_EXCEEDED (-13030) -> Network issues
                    throw e
                } catch (e: Exception) {
                    Log.e("DcsRepository", "Unexpected error during image upload", e)
                    throw e
                }
            }
            db.collection("reports").add(finalReport).await()
            true
        } catch (e: Exception) {
            Log.e("DcsRepository", "Error submitting report", e)
            false
        }
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
