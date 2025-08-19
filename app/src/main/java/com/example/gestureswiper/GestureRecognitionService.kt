package com.example.gestureswiper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ServiceLifecycleDispatcher
import kotlinx.coroutines.*
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class GestureRecognitionService : Service(), LifecycleOwner {

    companion object {
        const val CHANNEL_ID = "GestureRecognitionChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START_CAPTURE = "START_CAPTURE"
        const val ACTION_STOP_CAPTURE = "STOP_CAPTURE"
        const val EXTRA_AUTO_SWITCH_TO_YOUTUBE = "AUTO_SWITCH_TO_YOUTUBE"

        private const val TAG = "RecognitionService"
    }

    private val binder = GestureServiceBinder()
    private lateinit var gestureIntegration: GestureIntegrationHelper
    private lateinit var notificationManager: NotificationManager

    // Camera related
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var camera: Camera? = null

    // Service lifecycle
    private val dispatcher = ServiceLifecycleDispatcher(this)
    override val lifecycle: Lifecycle get() = dispatcher.lifecycle

    // Background execution
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val cameraExecutor: Executor = Executors.newSingleThreadExecutor()

    // State management
    private var isCapturing = false
    private var isPaused = false
    private var autoSwitchToYoutube = false

    // Handlers for pause/resume cycle
    private val pauseHandler = Handler(Looper.getMainLooper())
    private val pauseRunnable = Runnable {
        resumeCapture()
    }

    inner class GestureServiceBinder : Binder() {
        fun getService(): GestureRecognitionService = this@GestureRecognitionService
    }

    override fun onCreate() {
        super.onCreate()
        dispatcher.onServicePreSuperOnCreate()

        Log.d(TAG, "Service created")
        createNotificationChannel()
        initializeGestureIntegration()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        dispatcher.onServicePreSuperOnStart()

        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        when (intent?.action) {
            ACTION_START_CAPTURE -> {
                autoSwitchToYoutube = intent.getBooleanExtra(EXTRA_AUTO_SWITCH_TO_YOUTUBE, false)
                startGestureCapture()
            }
            ACTION_STOP_CAPTURE -> {
                stopGestureCapture()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        cleanup()
        serviceScope.cancel()
        dispatcher.onServicePreSuperOnDestroy()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Gesture Recognition Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Background gesture recognition service"
            setShowBadge(false)
        }

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, GestureRecognitionService::class.java).apply {
            action = ACTION_STOP_CAPTURE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Gesture Recognition Active")
            .setContentText(if (isCapturing) "Capturing gestures..." else "Standing by")
            .setSmallIcon(R.drawable.ic_gesture) // You'll need to add this icon
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_stop, "Stop", stopPendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification() {
        val notification = createNotification()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun initializeGestureIntegration() {
        gestureIntegration = GestureIntegrationHelper(this).apply {
            setGestureCallback { result ->
                Log.i(TAG, "Gesture detected: ${getGestureName(result.gestureClass)} " +
                        "with confidence: ${result.confidence}")

                pauseCapture()

                // Notify any bound activities
                // You can broadcast this result if needed
                sendGestureResult(result)
            }

            setHandDetectionCallback { detected ->
//                Log.d(TAG, "Hand detected: $detected")
            }
        }

        gestureIntegration.setup()
    }

    private fun startGestureCapture() {
        if (isCapturing) return

        Log.d(TAG, "Starting gesture capture")
        initializeCamera()

        if (autoSwitchToYoutube) {
            switchToYouTube()
        }
    }

    private fun stopGestureCapture() {
        Log.d(TAG, "Stopping gesture capture")
        isCapturing = false
        isPaused = false

        pauseHandler.removeCallbacks(pauseRunnable)

        try {
            cameraProvider?.unbindAll()
            gestureIntegration.stopGestureCapture()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping capture", e)
        }

        updateNotification()
    }

    private fun pauseCapture() {
        if (!isCapturing || isPaused) return

        Log.d(TAG, "Pausing capture for 10 seconds")
        isPaused = true
        gestureIntegration.stopGestureCapture()

        // Schedule resume after 10 seconds
        pauseHandler.postDelayed(pauseRunnable, 10000L)
        updateNotification()
        resumeCapture()
    }

    private fun resumeCapture() {
        if (!isCapturing || !isPaused) return

        Log.d(TAG, "Resuming capture")
        isPaused = false
        gestureIntegration.startGestureCapture()
        updateNotification()
    }

    private fun initializeCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCamera()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get camera provider", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCamera() {
        val cameraProvider = this.cameraProvider ?: return

        try {
            imageAnalysis = ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        if (isCapturing && !isPaused) {
                            gestureIntegration.processImage(imageProxy)
                        } else {
                            imageProxy.close()
                        }
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            // Unbind previous use cases
            cameraProvider.unbindAll()

            // Bind camera to lifecycle
            camera = cameraProvider.bindToLifecycle(
                this, // LifecycleOwner
                cameraSelector,
                imageAnalysis
            )

            isCapturing = true
            gestureIntegration.startGestureCapture()
            updateNotification()

            Log.i(TAG, "Camera bound successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Camera binding failed", e)
        }
    }

    private fun switchToYouTube() {
        try {
            val youtubeIntent = packageManager.getLaunchIntentForPackage("com.google.android.youtube")
            if (youtubeIntent != null) {
                youtubeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(youtubeIntent)
                Log.i(TAG, "Switched to YouTube")
            } else {
                Log.w(TAG, "YouTube app not found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to switch to YouTube", e)
        }
    }

    private fun sendGestureResult(result: ONNXGestureHelper.GestureResult) {
        // Broadcast the result to any listening components
        val intent = Intent("com.example.gestureswiper.GESTURE_DETECTED").apply {
            putExtra("gesture_class", result.gestureClass)
            putExtra("confidence", result.confidence)
            putExtra("inference_time", result.inferenceTime)
        }
        sendBroadcast(intent)
    }

    private fun cleanup() {
        try {
            pauseHandler.removeCallbacks(pauseRunnable)
            cameraProvider?.unbindAll()
            gestureIntegration.cleanup()
            cameraExecutor.let {
                if (it is java.util.concurrent.ExecutorService) {
                    it.shutdown()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }

    // Public methods for external control
    fun startCapture(switchToYoutube: Boolean = false) {
        autoSwitchToYoutube = switchToYoutube
        startGestureCapture()
    }

    fun stopCapture() {
        stopGestureCapture()
    }

    fun isServiceCapturing(): Boolean = isCapturing && !isPaused

    fun isServicePaused(): Boolean = isPaused

    private fun getGestureName(gestureClass: Int): String {
        return when (gestureClass) {
            0 -> "Swipe Up"
            1 -> "Swipe Down"
            2 -> "Thumbs Up"
            3 -> "Idle Gesture"
            else -> "Unknown Gesture"
        }
    }
}

/**
 * Helper class to manage the GestureRecognitionService
 */
object GestureServiceManager {

    fun startService(context: Context, autoSwitchToYoutube: Boolean = true) {
        val intent = Intent(context, GestureRecognitionService::class.java).apply {
            action = GestureRecognitionService.ACTION_START_CAPTURE
            putExtra(GestureRecognitionService.EXTRA_AUTO_SWITCH_TO_YOUTUBE, autoSwitchToYoutube)
        }

        context.startForegroundService(intent)
    }

    fun stopService(context: Context) {
        val intent = Intent(context, GestureRecognitionService::class.java).apply {
            action = GestureRecognitionService.ACTION_STOP_CAPTURE
        }
        context.stopService(intent)
    }

    fun isServiceRunning(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        @Suppress("DEPRECATION")
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (GestureRecognitionService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }
}