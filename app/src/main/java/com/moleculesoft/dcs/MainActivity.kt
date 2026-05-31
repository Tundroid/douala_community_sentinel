package com.moleculesoft.dcs

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.moleculesoft.dcs.service.SensorService
import com.moleculesoft.dcs.worker.DataSyncWorker
import com.moleculesoft.dcs.ui.theme.DoualaCommunitySentinelTheme
import com.moleculesoft.dcs.ui.MainScreen
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            startSensorService()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Request background location separately
                requestBackgroundLocation()
            }
        }
    }

    private fun requestBackgroundLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // This would normally be another launcher, but for now we just log/notify
            // Google Play requires showing a RATIONALE before requesting this.
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DoualaCommunitySentinelTheme {
                LaunchedEffect(Unit) {
                    requestPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.RECORD_AUDIO,
                            Manifest.permission.CAMERA,
                            Manifest.permission.POST_NOTIFICATIONS
                        )
                    )
                    setupWorkManager()
                }
                MainScreen()
            }
        }
    }

    private fun startSensorService() {
        val intent = Intent(this, SensorService::class.java)
        startForegroundService(intent)
    }

    private fun setupWorkManager() {
        val syncRequest = PeriodicWorkRequestBuilder<DataSyncWorker>(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "DataSyncWork",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }
}
