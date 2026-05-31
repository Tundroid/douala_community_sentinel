package com.moleculesoft.dcs.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.moleculesoft.dcs.DcsApplication
import com.moleculesoft.dcs.data.DcsRepository
import com.moleculesoft.dcs.data.PreferenceManager
import com.moleculesoft.dcs.data.UserStats
import com.moleculesoft.dcs.service.SensorService
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { DcsRepository() }
    val prefManager = remember { PreferenceManager(context) }
    
    var userStats by remember { mutableStateOf<UserStats?>(null) }
    val isServiceEnabled by prefManager.isServiceEnabled.collectAsState(initial = true)
    
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
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "User ID: ${stats.id}", style = MaterialTheme.typography.labelSmall)
                }
            }
        } ?: CircularProgressIndicator()

        Spacer(modifier = Modifier.height(32.dp))
        
        Text(text = "Privacy & Data", style = MaterialTheme.typography.titleMedium)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Background Collection")
                        Text(text = "Help map Douala while you move", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = isServiceEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                prefManager.setServiceEnabled(enabled)
                                val intent = Intent(context, SensorService::class.java)
                                if (enabled) {
                                    context.startForegroundService(intent)
                                } else {
                                    context.stopService(intent)
                                }
                            }
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = {
                        scope.launch {
                            DcsApplication.database.clearAllTables()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Clear My Local Data")
                }
            }
        }
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
