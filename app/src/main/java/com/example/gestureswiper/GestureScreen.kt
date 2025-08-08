package com.example.gestureswiper

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
//import androidx.compose.material3.LinearProgressIndicator
//import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestureRecognitionScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // State variables
    var isCapturing by remember { mutableStateOf(false) }
    var captureProgress by remember { mutableStateOf(0) }
    var maxProgress by remember { mutableStateOf(30) }
    var lastGestureResult by remember { mutableStateOf<String?>(null) }
    var gestureConfidence by remember { mutableStateOf(0f) }
    var isHandDetected by remember { mutableStateOf(false) }
    var inferenceTime by remember { mutableStateOf(0L) }

    // Gesture integration
    val gestureIntegration = remember {
        GestureIntegrationHelper(context).apply {
            setGestureCallback { result ->
                lastGestureResult = getGestureName(result.gestureClass)
                gestureConfidence = result.confidence
                inferenceTime = result.inferenceTime
            }

            setProgressCallback { current, total ->
                captureProgress = current
                maxProgress = total
            }

            setHandDetectionCallback { detected ->
                isHandDetected = detected
            }
        }
    }

    // Initialize on first composition
    LaunchedEffect(Unit) {
        gestureIntegration.setup()
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            gestureIntegration.cleanup()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Camera Preview
        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            gestureIntegration = gestureIntegration,
            lifecycleOwner = lifecycleOwner
        )

        // Hand Detection Indicator
        HandDetectionIndicator(
            isDetected = isHandDetected,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 60.dp)
        )

        // Progress Bar
        if (isCapturing && captureProgress > 0) {
            GestureCaptureProgress(
                progress = captureProgress,
                maxProgress = maxProgress,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 120.dp)
            )
        }

        // Gesture Result Display
        lastGestureResult?.let { gesture ->
            GestureResultCard(
                gesture = gesture,
                confidence = gestureConfidence,
                inferenceTime = inferenceTime,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 180.dp)
            )
        }

        // Control Panel
        ControlPanel(
            isCapturing = isCapturing,
            isHandDetected = isHandDetected,
            onStartCapture = {
                isCapturing = true
                captureProgress = 0
                lastGestureResult = null
                gestureIntegration.startGestureCapture()
            },
            onStopCapture = {
                isCapturing = false
                captureProgress = 0
                gestureIntegration.stopGestureCapture()
            },
            onClearResults = {
                lastGestureResult = null
                gestureConfidence = 0f
                inferenceTime = 0L
                gestureIntegration.clearBuffer()
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
        )

        // Status Text
        StatusText(
            isCapturing = isCapturing,
            isHandDetected = isHandDetected,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 200.dp)
        )
    }
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    gestureIntegration: GestureIntegrationHelper,
    lifecycleOwner: LifecycleOwner
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val previewView = PreviewView(context)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)           // VERY IMPORTANT LINE OF CODE
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(
                            ContextCompat.getMainExecutor(context)
                        ) { imageProxy ->
                            gestureIntegration.processImage(imageProxy)
                        }
                    }

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalyzer
                    )
                } catch (exc: Exception) {
                    // Handle camera binding error
                }
            }, ContextCompat.getMainExecutor(context))

            previewView
        }
    )
}

@Composable
fun HandDetectionIndicator(
    isDetected: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isDetected) Color.Green else Color.Red
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isDetected) Icons.Default.Check else Icons.Default.Clear,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isDetected) "Hand Detected" else "No Hand",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun GestureCaptureProgress(
    progress: Int,
    maxProgress: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Capturing Gesture",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progress.toFloat() / maxProgress.toFloat() },
                modifier = Modifier
                    .width(200.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$progress / $maxProgress frames",
                color = Color.White,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun GestureResultCard(
    gesture: String,
    confidence: Float,
    inferenceTime: Long,
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(true) }

    // Auto-hide after 3 seconds
    LaunchedEffect(gesture) {
        isVisible = true
        delay(3000)
        isVisible = false
    }

    if (isVisible) {
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = getGestureIcon(gesture),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = gesture,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Confidence: ${(confidence * 100).toInt()}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )

                Text(
                    text = "Time: ${inferenceTime}ms",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun ControlPanel(
    isCapturing: Boolean,
    isHandDetected: Boolean,
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit,
    onClearResults: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.8f)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Start/Stop Button
            FloatingActionButton(
                onClick = {
                    if (isCapturing) {
                        onStopCapture()
                    } else {
                        onStartCapture()
                    }
                },
                containerColor = if (isCapturing) Color.Red else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(60.dp)
            ) {
                Icon(
                    imageVector = if (isCapturing) Icons.Default.Clear else Icons.Default.PlayArrow,
                    contentDescription = if (isCapturing) "Stop" else "Start",
                    modifier = Modifier.size(28.dp),
                    tint = Color.White
                )
            }

            // Clear Button
            IconButton(
                onClick = onClearResults,
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        Color.Gray.copy(alpha = 0.3f),
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Clear",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun StatusText(
    isCapturing: Boolean,
    isHandDetected: Boolean,
    modifier: Modifier = Modifier
) {
    val statusText = when {
        isCapturing -> "Recording gesture... Keep your hand steady!"
        !isHandDetected -> "Show your hand to the camera"
        else -> "Press play to start gesture recognition"
    }

    Text(
        text = statusText,
        modifier = modifier.padding(horizontal = 32.dp),
        color = Color.White,
        fontSize = 16.sp,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.Medium
    )
}

// Helper functions
fun getGestureName(gestureClass: Int): String {
    return when (gestureClass) {
        0 -> "Swipe Up"
        1 -> "Swipe Down"
        2 -> "Thumbs Up"
        3 -> "Idle Gesture"
        else -> "Unknown Gesture"
    }
}

fun getGestureIcon(gesture: String) = when (gesture) {
    "Swipe Up" -> Icons.Default.KeyboardArrowUp
    "Swipe Down" -> Icons.Default.KeyboardArrowDown
    "Thumbs Up" -> Icons.Default.ThumbUp
    "Idle Gesture" -> Icons.Default.Home
    else -> Icons.Default.Warning
}