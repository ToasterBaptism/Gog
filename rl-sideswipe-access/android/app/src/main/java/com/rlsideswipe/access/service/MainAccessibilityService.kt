package com.rlsideswipe.access.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.lifecycle.Observer
import com.rlsideswipe.access.R

class MainAccessibilityService : AccessibilityService() {
    
    companion object {
        private const val TAG = "MainAccessibilityService"
        @Volatile
        private var instance: MainAccessibilityService? = null
        
        fun isRunning(): Boolean = instance != null
    }
    
    private var windowManager: WindowManager? = null
    private var overlayView: OverlayRenderer? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var overlayLayout: View? = null
    
    private var screenCaptureService: ScreenCaptureService? = null
    private var isServiceBound = false
    private var isOverlayAdded = false
    
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Service connected: $name")
            // Simplified - don't try to get service reference to avoid crashes
            isServiceBound = true
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Service disconnected: $name")
            screenCaptureService = null
            isServiceBound = false
        }
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "🔌 Accessibility service connected - setting up overlay")
        
        instance = this
        
        // TYPE_ACCESSIBILITY_OVERLAY does not require SYSTEM_ALERT_WINDOW; no overlay settings check needed here
        
        // Delay overlay setup to ensure service is fully initialized
        mainHandler.postDelayed({
            try {
                setupOverlay()
                observeScreenCaptureData()
                Log.i(TAG, "✅ Accessibility service setup completed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "🚨 Error during service setup", e)
                // Try to recover after a delay
                mainHandler.postDelayed({
                    Log.i(TAG, "🔄 Attempting service recovery...")
                    try {
                        setupOverlay()
                        observeScreenCaptureData()
                    } catch (e2: Exception) {
                        Log.e(TAG, "🚨 Service recovery failed", e2)
                    }
                }, 2000)
            }
        }, 500)
    }
    
    private fun setupOverlay() {
        if (isOverlayAdded) {
            Log.d(TAG, "Overlay already added, skipping")
            return
        }
        
        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            
            val inflater = LayoutInflater.from(this)
            overlayLayout = inflater.inflate(R.layout.overlay_ball_path, null)
            overlayView = overlayLayout?.findViewById(R.id.overlay_renderer)
            
            overlayParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            
            overlayLayout?.let { layout ->
                windowManager?.addView(layout, overlayParams)
                isOverlayAdded = true
                Log.i(TAG, "Accessibility overlay added successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add accessibility overlay", e)
            isOverlayAdded = false
        }
    }
    
    private fun observeScreenCaptureData() {
        try {
            // Don't try to bind to ScreenCaptureService to avoid crashes
            // The accessibility service will work independently for now
            Log.d(TAG, "Accessibility service running independently - no binding to ScreenCaptureService")
            
            // Just keep the service alive and stable
            // Future versions can add the binding back when ScreenCaptureService is more stable
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in observeScreenCaptureData", e)
        }
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Handle accessibility events if needed
        // For this application, we primarily use the service for overlay permissions
        event?.let {
            Log.v(TAG, "Accessibility event: ${it.eventType} from ${it.packageName}")
        }
    }
    
    override fun onInterrupt() {
        Log.w(TAG, "⚠️ Accessibility service interrupted - attempting to recover")
        
        // Try to recover the overlay after interruption
        mainHandler.postDelayed({
            try {
                if (!isOverlayAdded) {
                    Log.i(TAG, "🔄 Attempting to restore overlay after interruption")
                    setupOverlay()
                    observeScreenCaptureData()
                } else {
                    Log.d(TAG, "✅ Overlay still active after interruption")
                }
            } catch (e: Exception) {
                Log.e(TAG, "🚨 Failed to recover from interruption", e)
            }
        }, 1000)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Accessibility service being destroyed")
        
        instance = null
        
        // Clean up overlay
        try {
            if (isOverlayAdded && overlayLayout != null) {
                windowManager?.removeViewImmediate(overlayLayout)
                isOverlayAdded = false
                Log.d(TAG, "Overlay removed successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing overlay during destroy", e)
        }
        
        // Clean up service connection
        try {
            if (isServiceBound) {
                unbindService(serviceConnection)
                isServiceBound = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unbinding service", e)
        }
        
        // Clear references
        overlayLayout = null
        overlayView = null
        overlayParams = null
        windowManager = null
        screenCaptureService = null
        
        Log.i(TAG, "Accessibility service destroyed and cleaned up")
    }
    
    override fun onUnbind(intent: Intent?): Boolean {
        Log.w(TAG, "Accessibility service unbound")
        return super.onUnbind(intent)
    }
}