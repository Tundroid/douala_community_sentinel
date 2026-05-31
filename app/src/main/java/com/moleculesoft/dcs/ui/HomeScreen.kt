package com.moleculesoft.dcs.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.moleculesoft.dcs.data.DcsRepository
import com.moleculesoft.dcs.data.SensorData

@Composable
fun HomeScreen() {
    val doualaCenter = LatLng(4.0511, 9.7679)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(doualaCenter, 12f)
    }
    val repository = remember { DcsRepository() }
    val scope = rememberCoroutineScope()

    var lastSensorData by remember { mutableStateOf<SensorData?>(null) }
    var pendingSensorCount by remember { mutableStateOf(0) }
    var pendingReportCount by remember { mutableStateOf(0) }
    var statusMessage by remember { mutableStateOf("Collecting data...") }

    LaunchedEffect(Unit) {
        val latest = repository.getLatestSensorData()
        lastSensorData = latest
        pendingSensorCount = repository.getPendingSensorCount()
        pendingReportCount = repository.getPendingReportCount()
        statusMessage = if (latest != null) {
            "Last reading: ${latest.timestamp}"
        } else {
            statusMessage
        }
    }

    fun roadQualityLabel(variance: Double): String {
        return when {
            variance < 1.0 -> "Good"
            variance < 3.0 -> "Fair"
            variance < 6.0 -> "Poor"
            else -> "Critical"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Douala Community Sentinel",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    uiSettings = MapUiSettings(zoomControlsEnabled = false)
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                ) {
                    Text(
                        text = "Mini-Map: Community Hotspots",
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Citizen Status", style = MaterialTheme.typography.titleMedium)
                Text(text = "Sensor Service: Active")
                Text(text = statusMessage)
                lastSensorData?.let { sensor ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Road Quality: ${roadQualityLabel(sensor.accelerometerVariance)}")
                    Text(text = "Noise Level: ${String.format("%.1f", sensor.noiseLevelDb)} dB")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Pending uploads: $pendingSensorCount sensor records, $pendingReportCount reports")
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = {
                    scope.launch {
                        lastSensorData = repository.getLatestSensorData()
                        pendingSensorCount = repository.getPendingSensorCount()
                        pendingReportCount = repository.getPendingReportCount()
                        statusMessage = lastSensorData?.let { "Last reading: ${it.timestamp}" } ?: "No sensor data available yet."
                    }
                }) {
                    Text("Refresh Status")
                }
            }
        }
    }
}
