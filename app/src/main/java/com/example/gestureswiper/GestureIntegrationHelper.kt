package com.example.gestureswiper

import android.content.Context
import androidx.camera.core.ImageProxy
import com.google.mediapipe.tasks.vision.core.RunningMode

class GestureIntegrationHelper(private val context: Context) {

    private lateinit var handLandmarkerHelper: HandLandmarkerHelper
    private lateinit var gestureManager: GestureRecognitionManager

    private var gestureCallback: ((ONNXGestureHelper.GestureResult) -> Unit)? = null
    private var progressCallback: ((Int, Int) -> Unit)? = null
    private var handDetectionCallback: ((Boolean) -> Unit)? = null

    fun setup() {
        // Initialize gesture recognition manager
        gestureManager = GestureRecognitionManager(context, "tcn_model.onnx")

        // Set up internal callbacks
        gestureManager.setGestureCallback { result ->
            gestureCallback?.invoke(result)
        }

        gestureManager.setProgressCallback { current, total ->
            progressCallback?.invoke(current, total)
        }

        // Initialize hand landmarker with custom listener
        handLandmarkerHelper = HandLandmarkerHelper(
            context = context,
            runningMode = RunningMode.LIVE_STREAM,
            handLandmarkerHelperListener = object : HandLandmarkerHelper.LandmarkerListener {
                override fun onResults(resultBundle: HandLandmarkerHelper.ResultBundle) {
                    // Check if hand is detected
                    val hasHand = resultBundle.results.isNotEmpty()
                    handDetectionCallback?.invoke(hasHand)

                    // Forward to gesture manager
                    gestureManager.onResults(resultBundle)
                }

                override fun onError(error: String, errorCode: Int) {
                    gestureManager.onError(error, errorCode)
                }
            }
        )
    }

    fun processImage(imageProxy: ImageProxy) {
        handLandmarkerHelper.detectLiveStream(imageProxy, isFrontCamera = true)
    }

    fun setGestureCallback(callback: (ONNXGestureHelper.GestureResult) -> Unit) {
        gestureCallback = callback
    }

    fun setProgressCallback(callback: (Int, Int) -> Unit) {
        progressCallback = callback
    }

    fun setHandDetectionCallback(callback: (Boolean) -> Unit) {
        handDetectionCallback = callback
    }

    fun startGestureCapture() = gestureManager.startGestureRecognition()
    fun stopGestureCapture() = gestureManager.stopGestureRecognition()
    fun clearBuffer() = gestureManager.clearBuffer()

    fun cleanup() {
        gestureManager.cleanup()
        handLandmarkerHelper.clearHandLandmarker()
    }
}