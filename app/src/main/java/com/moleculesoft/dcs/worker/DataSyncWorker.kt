package com.moleculesoft.dcs.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.moleculesoft.dcs.data.DcsRepository

class DataSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val repository = DcsRepository()

    override suspend fun doWork(): Result {
        return try {
            val success = repository.syncAllPendingData()
            if (success) {
                Result.success()
            } else {
                Log.w("DataSyncWorker", "Not all pending data synced, retrying later.")
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        } catch (e: Exception) {
            Log.e("DataSyncWorker", "Data sync failed", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
