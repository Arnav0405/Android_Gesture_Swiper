package com.example.gestureswiper

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraBackgroundActivity : ComponentActivity() {

    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private lateinit var cameraExecutor: ExecutorService

    private var imageStreamCallback: ((ImageProxy) -> Unit)? = null

    companion object {
        private const val TAG = "MainActivity"
        private val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.CAMERA
        ).toTypedArray()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize camera executor
        cameraExecutor = Executors.newSingleThreadExecutor()
        Log.i("CameraBgAct", "Camera Executed")

        if (allPermissionsGranted()) {
            startCamera()
            // Start the livestream after a short delay to ensure camera is ready
            Handler(Looper.getMainLooper()).postDelayed({
                setupLivestream()
            }, 1000)
        } else {
            requestPermissions()
        }
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private val activityResultLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && !it.value) {
                    permissionGranted = false
                }
            }
            if (!permissionGranted) {
                Toast.makeText(
                    baseContext,
                    "Permission request denied",
                    Toast.LENGTH_SHORT
                ).show()
                Log.e(TAG, "Camera permission denied")
            } else {
                startCamera()
            }
        }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                // Used to bind the lifecycle of cameras to the lifecycle owner
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

                // Preview - not attached to any surface since we're running in background
                val preview = Preview.Builder().build()

                // ImageCapture
                imageCapture = ImageCapture.Builder().build()

                imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, ImageAnalysis.Analyzer { imageProxy ->
                            // Forward the image to the callback if set
                            imageStreamCallback?.invoke(imageProxy)
                            // Always close the image to prevent memory leaks
                            imageProxy.close()
                        })
                    }

                // Select FRONT camera
                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture,
                    imageAnalyzer
                )

                Log.i(TAG, "Front camera started successfully with image analysis")

                // Optional: Show toast for user feedback
                Toast.makeText(
                    this,
                    "Front camera started with livestream",
                    Toast.LENGTH_SHORT
                ).show()

            } catch (exc: Exception) {
                Log.e(TAG, "Camera startup failed", exc)
                Toast.makeText(
                    this,
                    "Camera startup failed: ${exc.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    fun startFrontCameraLivestream(onImageReceived: (ImageProxy) -> Unit) {
        imageStreamCallback = onImageReceived
        Log.i(TAG, "Front camera livestream callback registered")

        // If camera is not started yet, the callback will be used when camera starts
        // If camera is already started, the callback will immediately start receiving images
    }
    private fun setupLivestream() {
        startFrontCameraLivestream { imageProxy ->
            // This callback receives each frame from the front camera
            Log.d(TAG, "Received image: ${imageProxy.width}x${imageProxy.height}")
            Log.d(TAG, "Rotation: ${imageProxy.imageInfo.rotationDegrees}°")
            Log.d(TAG, "Timestamp: ${imageProxy.imageInfo.timestamp}")
            // You can process the ImageProxy here:
            // - Convert to Bitmap
            // - Run ML inference
            // - Apply filters
            // - Save to file
            // etc.

            // Example: Convert to bitmap (if needed)
            // val bitmap = imageProxyToBitmap(imageProxy)
        }
    }
    /**
     * Stop the livestream by removing the callback
     */
    fun stopLivestream() {
        imageStreamCallback = null
        Log.i(TAG, "Front camera livestream stopped")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLivestream()
        cameraExecutor.shutdown()
        Log.i(TAG, "Camera activity destroyed, executor shutdown")
    }
}