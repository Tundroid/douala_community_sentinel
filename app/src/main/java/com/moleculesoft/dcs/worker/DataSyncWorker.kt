package com.moleculesoft.dcs.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.moleculesoft.dcs.data.DcsRepository
import com.moleculesoft.dcs.data.SensorData

class DataSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private lateinit var repository: DcsRepository

    override suspend fun doWork(): Result {
        repository = DcsRepository()
        // In a real app, we would read buffered data from a local database (e.g., Room)
        // For this demo, we'll just simulate syncing a single entry
        return try {
            repository.saveSensorData(SensorData(neighborhood = "Bonaberi Demo"))
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
