package com.moleculesoft.dcs.data

import android.net.Uri
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.*

class DcsRepository {
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    suspend fun saveSensorData(data: SensorData) {
        db.collection("sensor_data").add(data).await()
    }

    suspend fun submitReport(report: UrbanReport, imageUri: Uri?) {
        var finalReport = report
        if (imageUri != null) {
            val imagePath = "reports/${UUID.randomUUID()}.jpg"
            val ref = storage.reference.child(imagePath)
            ref.putFile(imageUri).await()
            val downloadUrl = ref.downloadUrl.await()
            finalReport = report.copy(imageUrl = downloadUrl.toString())
        }
        db.collection("reports").add(finalReport).await()
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
