package com.moleculesoft.dcs

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moleculesoft.dcs.data.PreferenceManager
import com.moleculesoft.dcs.service.SensorService
import com.moleculesoft.dcs.worker.DataSyncWorker
import com.moleculesoft.dcs.ui.theme.DoualaCommunitySentinelTheme
import com.moleculesoft.dcs.ui.MainScreen
import com.moleculesoft.dcs.ui.OnboardingScreen
import com.moleculesoft.dcs.ui.LoginScreen
import com.moleculesoft.dcs.ui.AuthViewModel
import com.moleculesoft.dcs.ui.AuthState
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
            val authViewModel: AuthViewModel = viewModel()
            val authState by authViewModel.authState.collectAsState()
            
            var showOnboarding by remember { mutableStateOf<Boolean?>(null) }
            var showRationale by remember { mutableStateOf(false) }
            
            fun hasRequiredPermissions(): Boolean {
                return ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
            }

            LaunchedEffect(authState) {
                if (authState is AuthState.Authenticated) {
                    showOnboarding = !prefManager.isOnboardingCompleted.first()
                    if (showOnboarding == false) {
                        showRationale = !hasRequiredPermissions()
                    }
                }
            }

            DoualaCommunitySentinelTheme {
                when (authState) {
                    is AuthState.Loading -> {
                        // You might want a better splash/loading here
                    }
                    is AuthState.Unauthenticated, is AuthState.Error -> {
                        LoginScreen(onLoginSuccess = {
                            // AuthViewModel will update authState
                        })
                    }
                    is AuthState.Authenticated -> {
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
                                        text = "To accurately map flooding and potholes in your neighborhood, Douala Community Sentinel needs location and audio access.",
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

                                if (!showRationale) {
                                    LaunchedEffect(showOnboarding) {
                                        if (hasRequiredPermissions()) {
                                            setupWorkManager()
                                            checkAndStartService()
                                        }
                                    }
                                }

                                MainScreen()
                            }
                            else -> {} // Still loading pref
                        }
                    }
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
