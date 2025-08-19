package com.example.gestureswiper

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.gestureswiper.ui.theme.GestureSwiperTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val overlayGranted = Settings.canDrawOverlays(this)

        if (cameraGranted && overlayGranted) {
            Toast.makeText(this, "All permissions granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Some permissions are missing", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkAndRequestPermissions()

        setContent {
            GestureSwiperTheme {
                MainScreen(
                    onOpenAccessibilitySettings = { openAccessibilitySettings() }
                )
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }

        // Request permissions
        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

     fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
        Toast.makeText(
            this,
            "Please enable 'GestureSwiper' accessibility service",
            Toast.LENGTH_LONG
        ).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onOpenAccessibilitySettings: () -> Unit
) {
    var currentScreen by remember { mutableStateOf("main") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (currentScreen) {
            "main" -> MainMenuScreen(
                onNavigateToGesture = { currentScreen = "gesture" },
                onNavigateToBackground = { currentScreen = "background" }
            )
            "gesture" -> GestureRecognitionScreen()
            "background" -> BackgroundServiceScreen(
                onOpenAccessibilitySettings = onOpenAccessibilitySettings
            )
        }

        // Back button for non-main screens
        if (currentScreen != "main") {
            FloatingActionButton(
                onClick = { currentScreen = "main" },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 48.dp, start = 16.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        }
    }
}

@Composable
fun MainMenuScreen(
    onNavigateToGesture: () -> Unit,
    onNavigateToBackground: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "GestureSwiper",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "AI-Powered Gesture Recognition",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        MenuCard(
            title = "Live Gesture Recognition",
            description = "Test gesture recognition with camera preview",
            icon = Icons.Default.Check,
            onClick = onNavigateToGesture
        )

        Spacer(modifier = Modifier.height(16.dp))

        MenuCard(
            title = "Background Service",
            description = "Control gestures using Camera",
            icon = Icons.Default.Home,
            onClick = onNavigateToBackground
        )

        Spacer(modifier = Modifier.height(32.dp))

        PermissionsStatusCard()
    }
}

@Composable
fun BackgroundServiceScreen(
    onOpenAccessibilitySettings: () -> Unit
) {
    val context = LocalContext.current
    var isServiceRunning by remember { mutableStateOf(false) }
    var serviceConnected by remember { mutableStateOf(false) }
    var gestureResults by remember { mutableStateOf(listOf<GestureResultData>()) }
    var autoSwitchYouTube by remember { mutableStateOf(true) }

    // Gesture result handler
    val gestureHandler = remember {
        GestureResultHandler(context) { gestureClass, confidence, inferenceTime ->
            val gestureData = GestureResultData(
                gestureName = getGestureName(gestureClass),
                confidence = confidence,
                inferenceTime = inferenceTime,
                timestamp = System.currentTimeMillis()
            )
            gestureResults = listOf(gestureData) + gestureResults.take(9) // Keep last 10 results
        }
    }

    // Check service status periodically
    LaunchedEffect(Unit) {
        while (true) {
            isServiceRunning = GestureServiceManager.isServiceRunning(context)
            delay(1000)
        }
    }

    // Register/unregister gesture handler when service is running
    LaunchedEffect(isServiceRunning) {
        if (isServiceRunning) {
            gestureHandler.register()
            serviceConnected = true
        } else {
            gestureHandler.unregister()
            serviceConnected = false
        }
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            gestureHandler.unregister()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(56.dp)) // Account for back button

        Text(
            text = "Background Service Control",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Service Status Card
        ServiceStatusCard(
            isRunning = isServiceRunning,
            isConnected = serviceConnected
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Service Controls Card
        ServiceControlsCard(
            isServiceRunning = isServiceRunning,
            autoSwitchYouTube = autoSwitchYouTube,
            onAutoSwitchChanged = { autoSwitchYouTube = it },
            onStartService = {
                GestureServiceManager.startService(context, autoSwitchYouTube)
            },
            onStopService = {
                GestureServiceManager.stopService(context)
            },
            onOpenAccessibilitySettings = onOpenAccessibilitySettings
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Gesture Actions Info
        GestureActionsCard()

        Spacer(modifier = Modifier.height(16.dp))

        // Recent Gestures
        RecentGesturesCard(gestureResults)
    }
}

@Composable
fun ServiceStatusCard(
    isRunning: Boolean,
    isConnected: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isRunning)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.PlayArrow else Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (isRunning)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "Service Status",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isRunning) "Running" else "Stopped",
                        fontSize = 14.sp,
                        color = if (isRunning)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                }
            }

            if (isRunning) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Receiver: ${if (isConnected) "Connected" else "Disconnected"}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun ServiceControlsCard(
    isServiceRunning: Boolean,
    autoSwitchYouTube: Boolean,
    onAutoSwitchChanged: (Boolean) -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Service Controls",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Auto-switch to YouTube setting
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-switch to YouTube",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Open YouTube when service starts",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                Switch(
                    checked = autoSwitchYouTube,
                    onCheckedChange = onAutoSwitchChanged,
                    enabled = !isServiceRunning
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Service control buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onStartService,
                    enabled = !isServiceRunning,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start Service")
                }

                Button(
                    onClick = onStopService,
                    enabled = isServiceRunning,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Stop Service")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Accessibility settings button
            OutlinedButton(
                onClick = onOpenAccessibilitySettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Accessibility Settings")
            }
        }
    }
}

@Composable
fun RecentGesturesCard(gestureResults: List<GestureResultData>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Recent Gestures",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (gestureResults.isEmpty()) {
                Text(
                    text = "No gestures detected yet",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                )
            } else {
                gestureResults.forEach { result ->
                    GestureResultRow(result)
                    if (result != gestureResults.last()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GestureResultRow(result: GestureResultData) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = getGestureIcon(result.gestureName),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = result.gestureName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${(result.confidence * 100).toInt()}% confidence",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "${result.inferenceTime}ms",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = formatTimestamp(result.timestamp),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}


@Composable
fun MenuCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = description,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun PermissionsStatusCard() {
    val context = LocalContext.current
    var cameraPermission by remember { mutableStateOf(false) }
    var overlayPermission by remember { mutableStateOf(false) }
    var accessibilityPermission by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            cameraPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED

            overlayPermission =
                Settings.canDrawOverlays(context)

            delay(1000)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Permissions Status",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            PermissionRow("Camera", cameraPermission)
        }
    }
}

@Composable
fun PermissionRow(name: String, granted: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.Clear,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (granted) Color.Green else Color.Red
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (granted) "Granted" else "Denied",
                fontSize = 12.sp,
                color = if (granted) Color.Green else Color.Red
            )
        }
    }
}

@Composable
fun GestureActionsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Gesture Actions",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )

            Spacer(modifier = Modifier.height(12.dp))

            GestureActionRow("Swipe Up", "Swipe up on screen", Icons.Default.KeyboardArrowUp)
            GestureActionRow("Swipe Down", "Swipe down on screen", Icons.Default.KeyboardArrowDown)
            GestureActionRow("Double Tap", "Double tap center of screen", Icons.Default.Home)
            GestureActionRow("Idle", "No action", Icons.Default.Face)
        }
    }
}

@Composable
fun GestureActionRow(
    gesture: String,
    action: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = gesture,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = action,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

data class GestureResultData(
    val gestureName: String,
    val confidence: Float,
    val inferenceTime: Long,
    val timestamp: Long
)

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 1000 -> "Now"
        diff < 60000 -> "${diff / 1000}s ago"
        diff < 3600000 -> "${diff / 60000}m ago"
        else -> "${diff / 3600000}h ago"
    }
}