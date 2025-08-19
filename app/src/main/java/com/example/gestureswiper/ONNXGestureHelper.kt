package com.example.gestureswiper

import android.content.Context
import android.util.Log
import ai.onnxruntime.*
import java.nio.FloatBuffer
import kotlinx.coroutines.*

class ONNXGestureHelper(
    private val context: Context,
    private val modelFileName: String = "tcn_model.onnx",
    private val gestureListener: GestureListener? = null
) {

    private var ortSession: OrtSession? = null
    private var ortEnvironment: OrtEnvironment? = null

    private val frameBuffer = mutableListOf<List<Float>>()
    private val maxFrames = 30
    private val featuresPerFrame = 63

    private var isCollecting = false
    private var frameCount = 0

    private val inferenceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        setupONNXModel()
    }

    /**
     * Initialize the ONNX Runtime session
     */
    private fun setupONNXModel() {
        try {
            // Create ONNX Runtime environment
            ortEnvironment = OrtEnvironment.getEnvironment()

            // Load model from assets
            val modelBytes = context.assets.open(modelFileName).use { inputStream ->
                inputStream.readBytes()
            }

            // Create session options for optimization
            val sessionOptions = OrtSession.SessionOptions().apply {
                // Enable CPU optimizations
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                setIntraOpNumThreads(4)
                setInterOpNumThreads(2)
            }

            // Create ONNX session
            ortSession = ortEnvironment?.createSession(modelBytes, sessionOptions)

            Log.i(TAG, "ONNX model loaded successfully")
            logModelInfo()

        } catch (e: Exception) {
            val error = "Failed to initialize ONNX model: ${e.message}"
            Log.e(TAG, error, e)
            gestureListener?.onError(error)
        }
    }

    /**
     * Log model input/output information for debugging
     */
    private fun logModelInfo() {
        ortSession?.let { session ->
            try {
                Log.i(TAG, "Model Input Names: ${session.inputNames}")
                Log.i(TAG, "Model Output Names: ${session.outputNames}")

                session.inputInfo.forEach { (name, info) ->
                    Log.i(TAG, "Input '$name': ${info.info}")
                }

                session.outputInfo.forEach { (name, info) ->
                    Log.i(TAG, "Output '$name': ${info.info}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not log model info: ${e.message}")
            }
        }
    }

    /**
     * Start collecting frames for gesture recognition
     */
    fun startGestureCollection() {
        synchronized(this) {
            if (!isCollecting) {
                isCollecting = true
                frameCount = 0
                frameBuffer.clear()
                Log.d(TAG, "Started gesture collection")
                gestureListener?.onCollectionStarted()
            }
        }
    }

    /**
     * Stop collecting frames
     */
    fun stopGestureCollection() {
        synchronized(this) {
            if (isCollecting) {
                isCollecting = false
                frameCount = 0
                frameBuffer.clear()
                Log.d(TAG, "Stopped gesture collection")
                gestureListener?.onCollectionStopped()
            }
        }
    }

    /**
     * Add a new frame of landmarks to the buffer
     * This should be called from HandLandmarkerHelper's onResults callback
     */
    fun addFrame(landmarks: List<Float>) {
        if (landmarks.size != featuresPerFrame) {
            Log.w(TAG, "Invalid landmark size: ${landmarks.size}, expected: $featuresPerFrame")
            return
        }

        synchronized(this) {
            if (!isCollecting) {
                return // Not collecting frames right now
            }

            // Add new frame to buffer
            frameBuffer.add(landmarks)
            frameCount++

            Log.d(TAG, "Added frame $frameCount/$maxFrames")

            // Notify progress
            gestureListener?.onFrameAdded(frameCount, maxFrames)

            // If we have collected enough frames, perform inference
            if (frameCount >= maxFrames) {
                Log.d(TAG, "Collected $maxFrames frames, starting inference")
                performInference()

                // Reset collection automatically
                isCollecting = false
                frameCount = 0
            }
        }
    }

    /**
     * Manually trigger inference if we have some frames (useful for partial gestures)
     */
    fun triggerInference(): Boolean {
        synchronized(this) {
            return if (frameBuffer.isNotEmpty()) {
                Log.d(TAG, "Manual inference triggered with ${frameBuffer.size} frames")
                performInference()
                true
            } else {
                Log.w(TAG, "Cannot trigger inference: no frames collected")
                false
            }
        }
    }

    /**
     * Perform gesture inference on the current frame buffer
     */
    private fun performInference() {
        inferenceScope.launch {
            try {
                val inputArray = prepareInputArray()
                val result = runInference(inputArray)

                // Switch back to main thread for callback
                withContext(Dispatchers.Main) {
                    gestureListener?.onGestureDetected(result)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Inference failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    gestureListener?.onError("Inference failed: ${e.message}")
                }
            }
        }
    }

    /**
     * Convert frame buffer to input array for ONNX model
     * Shape: (1, 30, 63) - batch_size=1, sequence_length=30, features=63
     */
    private fun prepareInputArray(): Array<Array<FloatArray>> {
        val currentFrames = frameBuffer.toList() // Create a copy for thread safety

        // Create 3D array: [batch_size][sequence_length][features]
        val inputArray = Array(1) { // batch_size = 1
            Array(maxFrames) { frameIndex ->
                when {
                    frameIndex < currentFrames.size -> {
                        // Use actual frame data
                        currentFrames[frameIndex].toFloatArray()
                    }
                    currentFrames.isNotEmpty() -> {
                        // Pad with the last frame if we don't have enough frames
                        currentFrames.last().toFloatArray()
                    }
                    else -> {
                        // Fallback: pad with zeros
                        FloatArray(featuresPerFrame) { 0f }
                    }
                }
            }
        }

        Log.d(TAG, "Prepared input array: 1 x $maxFrames x $featuresPerFrame")
        Log.d(TAG, "Using ${currentFrames.size} actual frames, padding: ${maxFrames - currentFrames.size}")

        return inputArray
    }

    /**
     * Run ONNX inference
     */
    private suspend fun runInference(inputArray: Array<Array<FloatArray>>): GestureResult {
        return withContext(Dispatchers.IO) {
            val session = ortSession ?: throw IllegalStateException("ONNX session not initialized")

            val startTime = System.currentTimeMillis()

            // Flatten the 3D array to 1D for ONNX
            val flatInput = inputArray.flatMap { batch ->
                batch.flatMap { frame ->
                    frame.toList()
                }
            }.toFloatArray()

            // Create ONNX tensor
            val inputTensor = OnnxTensor.createTensor(
                ortEnvironment,
                FloatBuffer.wrap(flatInput),
                longArrayOf(1, maxFrames.toLong(), featuresPerFrame.toLong())
            )

            // Run inference
            val inputs = mapOf(session.inputNames.first() to inputTensor)
            val outputs = session.run(inputs)

            try {
//                Log.d("ONNXGestureHelper", "Outputs: $outputs")
//                Log.d("ONNXGestureHelper", "object: ${outputs.first().value}")

                val outputTensor = outputs.first().value as OnnxTensor
                val outputBuffer = outputTensor.floatBuffer
                val outputShape = outputTensor.info.shape

                val predictions = FloatArray(outputShape[1].toInt()) { i ->
                    outputBuffer.get(i)
                }

                val inferenceTime = System.currentTimeMillis() - startTime

                val maxIndex = predictions.indices.maxByOrNull { predictions[it] } ?: 0
                val maxConfidence = predictions[maxIndex]

                GestureResult(
                    gestureClass = maxIndex,
                    confidence = maxConfidence,
                    allProbabilities = predictions.toList(),
                    inferenceTime = inferenceTime
                )
            } finally {
                inputTensor.close()
                outputs.close()
            }
        }
    }

    fun getBufferStatus(): BufferStatus {
        synchronized(this) {
            return BufferStatus(
                currentFrames = frameBuffer.size,
                maxFrames = maxFrames,
                isReady = frameBuffer.size >= maxFrames,
                isCollecting = isCollecting,
                frameCount = frameCount
            )
        }
    }

    /**
     * Clear the frame buffer
     */
    fun clearBuffer() {
        synchronized(this) {
            frameBuffer.clear()
            frameCount = 0
            Log.d(TAG, "Frame buffer cleared")
        }
    }

    /**
     * Check if the helper is ready for inference
     */
    fun isReady(): Boolean {
        return ortSession != null
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        try {
            synchronized(this) {
                isCollecting = false
                frameBuffer.clear()
                frameCount = 0
            }

            inferenceScope.cancel()
            ortSession?.close()
            ortEnvironment?.close()

            Log.i(TAG, "ONNX resources cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "ONNXGestureHelper"
    }

    /**
     * Data class for gesture recognition results
     */
    data class GestureResult(
        val gestureClass: Int,
        val confidence: Float,
        val allProbabilities: List<Float>,
        val inferenceTime: Long
    )

    /**
     * Data class for buffer status
     */
    data class BufferStatus(
        val currentFrames: Int,
        val maxFrames: Int,
        val isReady: Boolean,
        val isCollecting: Boolean,
        val frameCount: Int
    )

    /**
     * Interface for gesture recognition callbacks
     */
    interface GestureListener {
        fun onGestureDetected(result: GestureResult)
        fun onError(error: String)
        fun onCollectionStarted() {}
        fun onCollectionStopped() {}
        fun onFrameAdded(currentFrame: Int, totalFrames: Int) {}
    }
}