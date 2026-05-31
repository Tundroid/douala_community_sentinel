package com.moleculesoft.dcs.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    var activeTab by remember { mutableIntStateOf(0) }

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
        Text(text = "Urban Insights", style = MaterialTheme.typography.headlineSmall)
        
        TabRow(selectedTabIndex = activeTab) {
            Tab(selected = activeTab == 0, onClick = { activeTab = 0 }, text = { Text("Feed") })
            Tab(selected = activeTab == 1, onClick = { activeTab = 1 }, text = { Text("Trends") })
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (activeTab == 0) {
            FeedView(
                neighborhoods = neighborhoods,
                selectedNeighborhood = selectedNeighborhood,
                onNeighborhoodSelected = { selectedNeighborhood = it },
                isLoading = isLoading,
                reports = filteredReports,
                onRefresh = {
                    isLoading = true
                    scope.launch {
                        allReports = repository.getRecentReports()
                        isLoading = false
                    }
                }
            )
        } else {
            TrendsView(reports = allReports)
        }
    }
}

@Composable
fun FeedView(
    neighborhoods: List<String>,
    selectedNeighborhood: String,
    onNeighborhoodSelected: (String) -> Unit,
    isLoading: Boolean,
    reports: List<UrbanReport>,
    onRefresh: () -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScrollableTabRow(
                selectedTabIndex = neighborhoods.indexOf(selectedNeighborhood),
                edgePadding = 0.dp,
                modifier = Modifier.weight(1f),
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                divider = {}
            ) {
                neighborhoods.forEach { neighborhood ->
                    Tab(
                        selected = selectedNeighborhood == neighborhood,
                        onClick = { onNeighborhoodSelected(neighborhood) },
                        text = { Text(neighborhood) }
                    )
                }
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
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
fun TrendsView(reports: List<UrbanReport>) {
    val stats = reports.groupBy { 
        when {
            it.description.contains("Bonaberi", true) -> "Bonaberi"
            it.description.contains("Akwa", true) -> "Akwa"
            it.description.contains("New Bell", true) -> "New Bell"
            else -> "Other"
        }
    }.mapValues { it.value.size }

    val recommendations = mutableListOf<String>()
    if ((stats["Bonaberi"] ?: 0) > 3) recommendations.add("High priority: Drainage clearance in Bonaberi")
    if ((stats["Akwa"] ?: 0) > 3) recommendations.add("Urgent: Pothole patching on Akwa main road")
    if (reports.any { it.type.contains("waste", true) }) recommendations.add("Optimize waste collection routes in detected hotspots")

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text(text = "Report Density by Neighborhood", style = MaterialTheme.typography.titleMedium)
        stats.forEach { (area, count) ->
            Text(text = "$area: $count reports")
            LinearProgressIndicator(
                progress = count.toFloat() / reports.size.coerceAtLeast(1),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(text = "Actionable Recommendations", style = MaterialTheme.typography.titleMedium)
        recommendations.forEach { rec ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Text(text = rec, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
        
        if (recommendations.isEmpty()) {
            Text(text = "Collecting more data for recommendations...", style = MaterialTheme.typography.bodySmall)
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
