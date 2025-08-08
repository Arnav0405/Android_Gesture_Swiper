package com.example.gestureswiper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.content.Context
import android.content.ComponentName
import android.provider.Settings
import java.lang.ref.WeakReference

class GestureToTouch : AccessibilityService() {
    companion object {
        private var instanceRef: WeakReference<GestureToTouch>? = null
        private var isRunning = false
        private var isGestureDetectionActive = false

        fun isServiceRunning(): Boolean = isRunning
        fun isGestureDetectionActive(): Boolean = isGestureDetectionActive

        fun startGestureDetection() {
            instanceRef?.get()?.startGestureRecognition()
        }

        fun stopGestureDetection() {
            instanceRef?.get()?.stopGestureRecognition()
        }

        // Check if accessibility service is actually enabled
        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val accessibilityEnabled = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED, 0
            )

            if (accessibilityEnabled == 1) {
                val services = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
                val expectedService = ComponentName(context, GestureToTouch::class.java)
                return services?.contains(expectedService.flattenToString()) == true
            }
            return false
        }
    }

    private lateinit var gestureIntegrationHelper: GestureIntegrationHelper
    private lateinit var backgroundCamera: BackgroundCamera
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var screenWidth = 0
    private var screenHeight = 0

    private val handler = Handler(Looper.getMainLooper())
    private var lastGestureTime = 0L
    private val gestureDelay = 1000L // 1 second delay between gestures

    override fun onCreate() {
        super.onCreate()
        // Use WeakReference to prevent memory leaks
        instanceRef = WeakReference(this)
        Log.d("GestureToTouch", "Service created")

        setupDisplayMetrics()
        setupGestureHelper()
    }

    override fun onServiceConnected() {
        isRunning = true
        Log.d("GestureToTouch", "Service connected")
        setupOverlay()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        isGestureDetectionActive = false

        // Clear the weak reference
        instanceRef?.clear()
        instanceRef = null

        stopGestureRecognition()
        removeOverlay()
        Log.d("GestureToTouch", "Service destroyed")
    }

    private fun setupDisplayMetrics() {
        val displayMetrics = DisplayMetrics()
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels
        Log.d("GestureToTouch", "Screen dimensions: ${screenWidth}x${screenHeight}")
    }

    private fun setupGestureHelper() {
        gestureIntegrationHelper = GestureIntegrationHelper(this)
        gestureIntegrationHelper.setup()

        gestureIntegrationHelper.setGestureCallback { result ->
            handleGestureResult(result)
        }

        gestureIntegrationHelper.setProgressCallback { current, total ->
            Log.d("GestureToTouch", "Gesture progress: $current/$total")
        }

        gestureIntegrationHelper.setHandDetectionCallback { hasHand ->
            // Optional: Update UI based on hand detection
        }
    }

    private fun setupOverlay() {
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            overlayView = FrameLayout(this).apply {
                setBackgroundColor(0x00000000) // Transparent
            }

            val params = WindowManager.LayoutParams(
                1, 1, // Minimal size
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }

            windowManager?.addView(overlayView, params)
            Log.d("GestureToTouch", "Overlay setup complete")
        } catch (e: Exception) {
            Log.e("GestureToTouch", "Failed to setup overlay", e)
        }
    }

    private fun removeOverlay() {
        try {
            overlayView?.let { view ->
                windowManager?.removeView(view)
                overlayView = null
            }
        } catch (e: Exception) {
            Log.e("GestureToTouch", "Error removing overlay", e)
        }
    }

    fun startGestureRecognition() {
        if (isGestureDetectionActive) {
            Log.d("GestureToTouch", "Gesture detection already active")
            return
        }

        try {
            backgroundCamera = BackgroundCamera(this) { imageProxy ->
                gestureIntegrationHelper.processImage(imageProxy)
            }

            backgroundCamera.startCamera()
            gestureIntegrationHelper.startGestureCapture()
            isGestureDetectionActive = true

            Log.d("GestureToTouch", "Gesture recognition started")
        } catch (e: Exception) {
            Log.e("GestureToTouch", "Failed to start gesture recognition", e)
            isGestureDetectionActive = false
        }
    }

    fun stopGestureRecognition() {
        if (!isGestureDetectionActive) {
            Log.d("GestureToTouch", "Gesture detection already inactive")
            return
        }

        try {
            if (::backgroundCamera.isInitialized) {
                backgroundCamera.cleanup()
            }

            gestureIntegrationHelper.stopGestureCapture()
            gestureIntegrationHelper.clearBuffer()
            isGestureDetectionActive = false

            Log.d("GestureToTouch", "Gesture recognition stopped")
        } catch (e: Exception) {
            Log.e("GestureToTouch", "Failed to stop gesture recognition", e)
        }
    }

    private fun handleGestureResult(result: ONNXGestureHelper.GestureResult) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastGestureTime < gestureDelay) {
            return // Too soon since last gesture
        }

        Log.d("GestureToTouch", "Gesture detected: ${result.gestureClass} (confidence: ${result.confidence})")

        when (result.gestureClass) {
            0 -> performSwipeUp()
            1 -> performSwipeDown()
            2 -> performDoubleTap()
            3 -> { /* Do nothing for idle gesture */ }
            else -> Log.w("GestureToTouch", "Unknown gesture class: ${result.gestureClass}")
        }

        lastGestureTime = currentTime
    }

    private fun performSwipeUp() {
        val startY = (screenHeight * 0.8f).toInt()
        val endY = (screenHeight * 0.2f).toInt()
        val centerX = screenWidth / 2

        performSwipeGesture(centerX, startY, centerX, endY, 300)
        Log.d("GestureToTouch", "Performed swipe up")
    }

    private fun performSwipeDown() {
        val startY = (screenHeight * 0.2f).toInt()
        val endY = (screenHeight * 0.8f).toInt()
        val centerX = screenWidth / 2

        performSwipeGesture(centerX, startY, centerX, endY, 300)
        Log.d("GestureToTouch", "Performed swipe down")
    }

    private fun performDoubleTap() {
        val centerX = screenWidth / 2
        val centerY = screenHeight / 2

        // First tap
        performTapGesture(centerX, centerY)

        // Second tap after short delay
        handler.postDelayed({
            performTapGesture(centerX, centerY)
        }, 100)

        Log.d("GestureToTouch", "Performed double tap")
    }

    private fun performSwipeGesture(startX: Int, startY: Int, endX: Int, endY: Int, duration: Long) {
        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(endX.toFloat(), endY.toFloat())
        }

        val gestureBuilder = GestureDescription.Builder()
        val strokeDescription = GestureDescription.StrokeDescription(path, 0, duration)
        gestureBuilder.addStroke(strokeDescription)

        val gesture = gestureBuilder.build()
        dispatchGesture(gesture, null, null)
    }

    private fun performTapGesture(x: Int, y: Int) {
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
        }

        val gestureBuilder = GestureDescription.Builder()
        val strokeDescription = GestureDescription.StrokeDescription(path, 0, 50)
        gestureBuilder.addStroke(strokeDescription)

        val gesture = gestureBuilder.build()
        dispatchGesture(gesture, null, null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Required override - can be used for additional accessibility features if needed
    }

    override fun onInterrupt() {
        Log.d("GestureToTouch", "Service interrupted")
        stopGestureRecognition()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
}