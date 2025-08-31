package com.rlsideswipe.access.util

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.rlsideswipe.access.service.ScreenCaptureService

class SelfHealing(private val context: Context) {
    
    companion object {
        private const val TAG = "SelfHealing"
        private const val FRAME_STALL_TIMEOUT_MS = 2000L
        private const val CHECK_INTERVAL_MS = 1000L
    }
    
    private val handler = Handler(Looper.getMainLooper())
    private var lastFrameTime = 0L
    private var isMonitoring = false
    
    private val watchdogRunnable = object : Runnable {
        override fun run() {
            if (isMonitoring) {
                checkFrameStall()
                handler.postDelayed(this, CHECK_INTERVAL_MS)
            }
        }
    }
    
    fun startMonitoring() {
        if (!isMonitoring) {
            isMonitoring = true
            lastFrameTime = System.currentTimeMillis()
            handler.post(watchdogRunnable)
            Log.d(TAG, "Self-healing monitoring started")
        }
    }
    
    fun stopMonitoring() {
        isMonitoring = false
        handler.removeCallbacks(watchdogRunnable)
        Log.d(TAG, "Self-healing monitoring stopped")
    }
    
    fun reportFrameProcessed() {
        lastFrameTime = System.currentTimeMillis()
    }
    
    private fun checkFrameStall() {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastFrame = currentTime - lastFrameTime
        
        if (timeSinceLastFrame > FRAME_STALL_TIMEOUT_MS) {
            Log.w(TAG, "Frame stall detected (${timeSinceLastFrame}ms), restarting capture service")
            restartScreenCaptureService()
            lastFrameTime = currentTime // Reset timer
        }
    }
    
    private fun restartScreenCaptureService() {
        try {
            // Stop the service
            val stopIntent = Intent(context, ScreenCaptureService::class.java)
            context.stopService(stopIntent)
            
            // Wait a moment before restarting
            handler.postDelayed({
                try {
                    // Check if we have a stored capture intent to restart with
                    val sharedPrefs = context.getSharedPreferences("screen_capture", Context.MODE_PRIVATE)
                    val hasStoredIntent = sharedPrefs.getBoolean("has_capture_intent", false)
                    
                    if (hasStoredIntent) {
                        // In a real implementation, we would need to request MediaProjection again
                        // For now, just log that restart would be needed
                        Log.d(TAG, "Screen capture service restart would require new MediaProjection permission")
                    } else {
                        Log.d(TAG, "No stored capture intent available for restart")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to restart screen capture service", e)
                }
            }, 500)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during service restart", e)
        }
    }
    
    fun rebindOverlay() {
        // This would be called when window changes are detected
        // to ensure the overlay remains properly attached
        Log.d(TAG, "Overlay rebind requested")
        
        // In a real implementation, this would communicate with the AccessibilityService
        // to recreate the overlay if needed
    }
}