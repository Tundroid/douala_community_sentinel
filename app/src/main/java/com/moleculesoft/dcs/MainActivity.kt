package com.moleculesoft.dcs

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.moleculesoft.dcs.data.PreferenceManager
import com.moleculesoft.dcs.service.SensorService
import com.moleculesoft.dcs.worker.DataSyncWorker
import com.moleculesoft.dcs.ui.theme.DoualaCommunitySentinelTheme
import com.moleculesoft.dcs.ui.MainScreen
import com.moleculesoft.dcs.ui.OnboardingScreen
import com.moleculesoft.dcs.ui.components.PermissionRationaleDialog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            checkAndStartService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val prefManager = remember { PreferenceManager(context) }
            
            var showOnboarding by remember { mutableStateOf<Boolean?>(null) }
            var showRationale by remember { mutableStateOf(false) }
            
            LaunchedEffect(Unit) {
                showOnboarding = !prefManager.isOnboardingCompleted.first()
                if (showOnboarding == false) {
                    showRationale = true
                }
            }

            DoualaCommunitySentinelTheme {
                when (showOnboarding) {
                    true -> {
                        OnboardingScreen(onComplete = {
                            scope.launch {
                                prefManager.setOnboardingCompleted(true)
                                showOnboarding = false
                                showRationale = true
                            }
                        })
                    }
                    false -> {
                        if (showRationale) {
                            PermissionRationaleDialog(
                                title = "Community Safety Needs Location",
                                text = "To accurately map flooding and potholes in your neighborhood, Douala Community Sentinel needs location and camera access.",
                                onConfirm = {
                                    showRationale = false
                                    requestPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION,
                                            Manifest.permission.RECORD_AUDIO,
                                            Manifest.permission.CAMERA,
                                            Manifest.permission.POST_NOTIFICATIONS
                                        )
                                    )
                                },
                                onDismiss = { showRationale = false }
                            )
                        }
                        
                        LaunchedEffect(Unit) {
                            setupWorkManager()
                        }
                        MainScreen()
                    }
                    else -> {} // Still loading pref
                }
            }
        }
    }

    private fun checkAndStartService() {
        val prefManager = PreferenceManager(this)
        lifecycleScope.launch {
            if (prefManager.isServiceEnabled.first()) {
                val intent = Intent(this@MainActivity, SensorService::class.java)
                startForegroundService(intent)
            }
        }
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
