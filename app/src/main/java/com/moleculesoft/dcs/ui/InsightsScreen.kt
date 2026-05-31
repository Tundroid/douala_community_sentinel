package com.moleculesoft.dcs.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
    var allReports by remember { mutableStateOf<List<UrbanReport>>(emptyList()) }
    var selectedNeighborhood by remember { mutableStateOf("All") }
    var isLoading by remember { mutableStateOf(true) }

    val neighborhoods = listOf("All", "Bonaberi", "Akwa", "New Bell", "Douala")

    LaunchedEffect(Unit) {
        allReports = repository.getRecentReports()
        isLoading = false
    }

    val filteredReports = if (selectedNeighborhood == "All") {
        allReports
    } else {
        allReports.filter { 
            it.type.contains(selectedNeighborhood, ignoreCase = true) || 
            it.description.contains(selectedNeighborhood, ignoreCase = true) 
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Urban Insights", style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = { 
                isLoading = true
                scope.launch {
                    allReports = repository.getRecentReports()
                    isLoading = false
                }
            }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        ScrollableTabRow(
            selectedTabIndex = neighborhoods.indexOf(selectedNeighborhood),
            edgePadding = 0.dp,
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            divider = {}
        ) {
            neighborhoods.forEach { neighborhood ->
                Tab(
                    selected = selectedNeighborhood == neighborhood,
                    onClick = { selectedNeighborhood = neighborhood },
                    text = { Text(neighborhood) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            LazyColumn {
                items(filteredReports) { report ->
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
