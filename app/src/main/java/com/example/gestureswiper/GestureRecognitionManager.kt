package com.example.gestureswiper

import android.content.Context
import android.util.Log

/**
 * Integration helper that connects HandLandmarkerHelper with ONNXGestureHelper
 * This class manages the flow of collecting 30 frames and triggering gesture recognition
 */
class GestureRecognitionManager(
    context: Context,
    modelFileName: String = "tcn_model.onnx"
) : HandLandmarkerHelper.LandmarkerListener, ONNXGestureHelper.GestureListener {

    private val onnxHelper = ONNXGestureHelper(context, modelFileName, this)
    private var gestureCallback: ((ONNXGestureHelper.GestureResult) -> Unit)? = null
    private var progressCallback: ((Int, Int) -> Unit)? = null

    // Gesture collection state
    private var isGestureActive = false
    private var gestureStartTime = 0L
    private val gestureTimeoutMs = 3000L // 3 seconds timeout

    fun setGestureCallback(callback: (ONNXGestureHelper.GestureResult) -> Unit) {
        gestureCallback = callback
    }

    fun setProgressCallback(callback: (Int, Int) -> Unit) {
        progressCallback = callback
    }

    /**
     * Start collecting frames for a gesture (call this when gesture should begin)
     */
    fun startGestureRecognition() {
        if (!isGestureActive) {
            isGestureActive = true
            gestureStartTime = System.currentTimeMillis()
            onnxHelper.startGestureCollection()
            Log.i("GestureManager", "Started gesture recognition")
        }
    }

    /**
     * Stop collecting frames (call this when gesture should end)
     */
    fun stopGestureRecognition() {
        if (isGestureActive) {
            isGestureActive = false
            onnxHelper.stopGestureCollection()
            Log.i("GestureManager", "Stopped gesture recognition")
        }
    }

    /**
     * Toggle gesture recognition state
     */
    fun toggleGestureRecognition() {
        if (isGestureActive) {
            stopGestureRecognition()
        } else {
            startGestureRecognition()
        }
    }

    /**
     * Manually trigger inference with current frames
     */
    fun recognizeCurrentGesture(): Boolean {
        return onnxHelper.triggerInference()
    }

    // HandLandmarkerHelper.LandmarkerListener implementation
    override fun onResults(resultBundle: HandLandmarkerHelper.ResultBundle) {
        // Check for timeout
        if (isGestureActive && System.currentTimeMillis() - gestureStartTime > gestureTimeoutMs) {
            Log.w("GestureManager", "Gesture collection timeout, stopping")
            stopGestureRecognition()
            return
        }

        // Only process if gesture recognition is active
        if (isGestureActive) {
            // Feed landmarks to ONNX helper
            onnxHelper.addFrame(resultBundle.results)
        }
    }

    override fun onError(error: String, errorCode: Int) {
        Log.e("GestureManager", "HandLandmarker error: $error")
        // Stop gesture recognition on error
        if (isGestureActive) {
            stopGestureRecognition()
        }
    }

    // ONNXGestureHelper.GestureListener implementation
    override fun onGestureDetected(result: ONNXGestureHelper.GestureResult) {
        Log.i("GestureManager", "Gesture detected: Class ${result.gestureClass}, " +
                "Confidence: ${result.confidence}, Inference time: ${result.inferenceTime}ms")

        gestureCallback?.invoke(result)

        // Automatically stop collection after successful recognition
        if (isGestureActive) {
            stopGestureRecognition()
        }
    }

    override fun onError(error: String) {
        Log.e("GestureManager", "ONNX error: $error")
        if (isGestureActive) {
            stopGestureRecognition()
        }
    }

    override fun onCollectionStarted() {
        Log.d("GestureManager", "Frame collection started")
    }

    override fun onCollectionStopped() {
        Log.d("GestureManager", "Frame collection stopped")
    }

    override fun onFrameAdded(currentFrame: Int, totalFrames: Int) {
        Log.d("GestureManager", "Frame progress: $currentFrame/$totalFrames")
        progressCallback?.invoke(currentFrame, totalFrames)
    }

    // Utility methods
    fun isCollecting(): Boolean = isGestureActive
    fun getBufferStatus() = onnxHelper.getBufferStatus()
    fun clearBuffer() = onnxHelper.clearBuffer()
    fun cleanup() = onnxHelper.cleanup()
}