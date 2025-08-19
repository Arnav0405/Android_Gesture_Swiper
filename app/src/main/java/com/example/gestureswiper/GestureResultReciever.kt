package com.example.gestureswiper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log

/**
 * Broadcast receiver to handle gesture results from the background service
 */
class GestureResultReceiver(
    private val onGestureDetected: (Int, Float, Long) -> Unit
) : BroadcastReceiver() {

    companion object {
        const val ACTION_GESTURE_DETECTED = "com.example.gestureswiper.GESTURE_DETECTED"
        private const val TAG = "GestureResultReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == ACTION_GESTURE_DETECTED) {
            val gestureClass = intent.getIntExtra("gesture_class", -1)
            val confidence = intent.getFloatExtra("confidence", 0f)
            val inferenceTime = intent.getLongExtra("inference_time", 0L)

            Log.d(TAG, "Received gesture result: class=$gestureClass, confidence=$confidence")

            if (gestureClass != -1) {
                onGestureDetected(gestureClass, confidence, inferenceTime)
            }
        }
    }

    /**
     * Get the intent filter for this receiver
     */
    fun getIntentFilter(): IntentFilter {
        return IntentFilter(ACTION_GESTURE_DETECTED)
    }
}

/**
 * Helper class to easily register and unregister the gesture result receiver
 */
class GestureResultHandler(
    private val context: Context,
    private val onGestureDetected: (gestureClass: Int, confidence: Float, inferenceTime: Long) -> Unit
) {
    private var receiver: GestureResultReceiver? = null

    fun register() {
        unregister() // Ensure we don't double-register

        receiver = GestureResultReceiver(onGestureDetected)
        context.registerReceiver(receiver, receiver!!.getIntentFilter())
        Log.d("GestureResultHandler", "Registered gesture result receiver")
    }

    fun unregister() {
        receiver?.let {
            try {
                context.unregisterReceiver(it)
                Log.d("GestureResultHandler", "Unregistered gesture result receiver")
            } catch (e: Exception) {
                Log.w("GestureResultHandler", "Failed to unregister receiver", e)
            }
        }
        receiver = null
    }
}