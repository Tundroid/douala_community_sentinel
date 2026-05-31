package com.moleculesoft.dcs.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import com.moleculesoft.dcs.data.DcsRepository
import com.moleculesoft.dcs.data.UrbanReport
import kotlinx.coroutines.launch
import java.io.File
import java.util.*

import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun ReportScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { DcsRepository() }
    val snackbarHostState = remember { SnackbarHostState() }
    
    var type by remember { mutableStateOf("Flooding") }
    var description by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    var photoCount by remember { mutableIntStateOf(0) }
    
    val tempUri = remember {
        val file = File(context.cacheDir, "temp_report_image.jpg")
        FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            imageUri = null // Reset to force recomposition
            imageUri = tempUri
            photoCount++ // Increment to force Coil reload
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(text = "Submit Urban Report", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = type,
                onValueChange = { type = it },
                label = { Text("Issue Type (e.g. Flooding, Waste, Pothole)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (imageUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageUri)
                        .build(),
                    contentDescription = "Captured Issue",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            Button(
                onClick = { cameraLauncher.launch(tempUri) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text(if (imageUri == null) "Take Photo" else "Retake Photo")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (isUploading) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            } else {
                Button(
                    onClick = {
                        scope.launch {
                            isUploading = true
                            android.util.Log.d("ReportScreen", "Submitting report. Image URI: $imageUri")
                            val report = UrbanReport(
                                id = UUID.randomUUID().toString(),
                                type = type,
                                description = description
                            )
                            val success = repository.submitReport(report, imageUri)
                            isUploading = false
                            
                            if (success) {
                                snackbarHostState.showSnackbar("Report submitted successfully!")
                                // Reset form
                                description = ""
                                imageUri = null
                            } else {
                                snackbarHostState.showSnackbar("Failed to submit report. Please check your connection or try again later.")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = description.isNotBlank()
                ) {
                    Text("Submit Report")
                }
            }
        }
    }
}
