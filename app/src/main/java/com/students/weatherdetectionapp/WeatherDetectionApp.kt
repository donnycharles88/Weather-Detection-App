@file:OptIn(ExperimentalMaterial3Api::class)

package com.students.weatherdetectionapp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

@Composable
fun WeatherDetectionApp() {
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    val resultsState = remember {
        mutableStateOf(
            mapOf(
                "cloudy" to 0f,
                "rain" to 0f,
                "shine" to 0f,
                "sunrise" to 0f
            )
        )
    }
    val results by resultsState

    val context = LocalContext.current

    val classifierHelper = remember {
        ImageClassifierHelper(
            threshold = 0.1f,
            maxResults = 4,
            context = context,
            classifierListener = object : ImageClassifierHelper.ClassifierListener {
                override fun onError(error: String) {
                    Log.e("WeatherDetection", error)
                }

                override fun onResults(results: List<ImageClassifierHelper.Classification>, inferenceTime: Long) {
                    val newResults = mutableMapOf<String, Float>()
                    newResults["cloudy"] = 0f
                    newResults["rain"] = 0f
                    newResults["shine"] = 0f
                    newResults["sunrise"] = 0f

                    results.forEach { classification ->
                        newResults[classification.label] = classification.score
                    }

                    resultsState.value = newResults
                }
            }
        )
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { takenBitmap ->
        bitmap = takenBitmap
        takenBitmap?.let { classifierHelper.classifyImage(it) }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        imageUri = uri
        uri?.let {
            bitmap = uriToBitmap(context, it)
            if (bitmap == null) {
                Log.e("WeatherDetection", "Failed to convert URI to Bitmap")
            } else {
                classifierHelper.classifyImage(bitmap!!)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraLauncher.launch()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weather Detection App") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFE0E0E0)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFE0E0E0))
            ) {
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Result :",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFE0E0E0)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ResultRow("cloudy", results["cloudy"] ?: 0f)
                    ResultRow("rain", results["rain"] ?: 0f)
                    ResultRow("shine", results["shine"] ?: 0f)
                    ResultRow("sunrise", results["sunrise"] ?: 0f)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = {
                        when {
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED -> {
                                cameraLauncher.launch()
                            }
                            else -> {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE0E0E0),
                        contentColor = Color.Black
                    )
                ) {
                    Text("Take a photo")
                }

                Button(
                    onClick = { galleryLauncher.launch("image/*") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE0E0E0),
                        contentColor = Color.Black
                    )
                ) {
                    Text("Upload image")
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            classifierHelper.close()
        }
    }
}

@Composable
fun ResultRow(label: String, percentage: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label)
        Text(text = "${(percentage * 100).toInt()}%")
    }
}

