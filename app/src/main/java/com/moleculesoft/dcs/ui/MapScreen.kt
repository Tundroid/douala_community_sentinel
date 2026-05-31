package com.moleculesoft.dcs.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.moleculesoft.dcs.data.DcsRepository
import com.moleculesoft.dcs.data.UrbanReport

@Composable
fun MapScreen() {
    val doualaCenter = LatLng(4.0511, 9.7679)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(doualaCenter, 12f)
    }

    val repository = remember { DcsRepository() }
    var reports by remember { mutableStateOf<List<UrbanReport>>(emptyList()) }

    LaunchedEffect(Unit) {
        reports = repository.getRecentReports()
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState
    ) {
        reports.forEach { report ->
            if (report.latitude != null && report.longitude != null) {
                val position = LatLng(report.latitude, report.longitude)
                Marker(
                    state = remember { MarkerState(position = position) },
                    title = report.type,
                    snippet = report.description
                )
            }
        }
    }
}
