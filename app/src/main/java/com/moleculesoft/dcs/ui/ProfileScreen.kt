package com.moleculesoft.dcs.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moleculesoft.dcs.data.DcsRepository
import com.moleculesoft.dcs.data.UserStats

@Composable
fun ProfileScreen() {
    val repository = remember { DcsRepository() }
    var userStats by remember { mutableStateOf<UserStats?>(null) }
    
    LaunchedEffect(Unit) {
        userStats = repository.getUserStats()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(text = "Citizen Sentinel Profile", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(24.dp))
        
        userStats?.let { stats ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Community Points: ${stats.points}", style = MaterialTheme.typography.titleLarge)
                    Text(text = "Sentinel Rank: ${getRank(stats.points)}")
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "User ID: ${stats.id}", style = MaterialTheme.typography.labelSmall)
                }
            }
        } ?: CircularProgressIndicator()
    }
}

fun getRank(points: Long): String {
    return when {
        points < 50 -> "Bronze Sentinel"
        points < 200 -> "Silver Sentinel"
        points < 500 -> "Gold Sentinel"
        else -> "Douala Guardian"
    }
}
