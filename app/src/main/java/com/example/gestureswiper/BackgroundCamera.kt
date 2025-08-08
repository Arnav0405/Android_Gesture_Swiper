package com.example.gestureswiper

import android.content.Context
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class BackgroundCamera(
    private val context: Context,
    private val onImageAnalyzed: (ImageProxy) -> Unit
) : LifecycleOwner {

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private val lifecycleRegistry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle = lifecycleRegistry

    init {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    fun startCamera() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
                Log.d("BackgroundCamera", "Camera started successfully")
            } catch (exception: Exception) {
                Log.e("BackgroundCamera", "Camera initialization failed", exception)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
            .build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        imageAnalyzer?.setAnalyzer(cameraExecutor) { imageProxy ->
            try {
                onImageAnalyzed(imageProxy)
            } catch (exception: Exception) {
                Log.e("BackgroundCamera", "Image analysis failed", exception)
            } finally {
                imageProxy.close()
            }
        }

        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                imageAnalyzer
            )
            Log.d("BackgroundCamera", "Camera use cases bound successfully")
        } catch (exception: Exception) {
            Log.e("BackgroundCamera", "Use case binding failed", exception)
        }
    }

    fun stopCamera() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        try {
            cameraProvider?.unbindAll()
            camera = null
            imageAnalyzer = null
            Log.d("BackgroundCamera", "Camera stopped successfully")
        } catch (exception: Exception) {
            Log.e("BackgroundCamera", "Error stopping camera", exception)
        }
    }

    fun cleanup() {
        stopCamera()
        cameraExecutor.shutdown()
    }

}