package com.moleculesoft.dcs.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.moleculesoft.dcs.data.DcsRepository
import com.moleculesoft.dcs.data.UrbanReport
import kotlinx.coroutines.launch

@Composable
fun InsightsScreen() {
    val repository = remember { DcsRepository() }
    val scope = rememberCoroutineScope()
    var reports by remember { mutableStateOf<List<UrbanReport>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        reports = repository.getRecentReports()
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "Urban Insights", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { 
                isLoading = true
                scope.launch {
                    reports = repository.getRecentReports()
                    isLoading = false
                }
            }) {
                Text("Refresh")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        } else {
            LazyColumn {
                items(reports) { report ->
                    ReportCard(report)
                }
            }
        }
    }
}

@Composable
fun ReportCard(report: UrbanReport) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = report.type, style = MaterialTheme.typography.titleMedium)
            Text(text = report.description, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "Time: ${report.timestamp}",
                style = MaterialTheme.typography.labelSmall
            )
            
            report.imageUrl?.let { url ->
                Spacer(modifier = Modifier.height(8.dp))
                Image(
                    painter = rememberAsyncImagePainter(url),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}
