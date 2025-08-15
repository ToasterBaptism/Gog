package com.rlsideswipe.access.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.PixelFormat
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.lifecycle.Observer
import com.rlsideswipe.access.R

class MainAccessibilityService : AccessibilityService() {
    
    companion object {
        private const val TAG = "AccessibilityService"
    }
    
    private var windowManager: WindowManager? = null
    private var overlayView: OverlayRenderer? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    
    private var screenCaptureService: ScreenCaptureService? = null
    private var isServiceBound = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            // Note: ScreenCaptureService doesn't return a binder, so this won't be called
            // We'll observe the static LiveData instead
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            screenCaptureService = null
            isServiceBound = false
        }
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service connected")
        
        setupOverlay()
        observeScreenCaptureData()
    }
    
    private fun setupOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        val inflater = LayoutInflater.from(this)
        val overlayLayout = inflater.inflate(R.layout.overlay_ball_path, null)
        overlayView = overlayLayout.findViewById(R.id.overlay_renderer)
        
        overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        
        try {
            windowManager?.addView(overlayLayout, overlayParams)
            Log.d(TAG, "Overlay added successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay", e)
        }
    }
    
    private fun observeScreenCaptureData() {
        // In a real implementation, we would need a way to observe the ScreenCaptureService data
        // For now, this is a stub that would be connected to the service's LiveData
        
        // This would typically be done through a bound service or static references
        // For the stub implementation, we'll leave this empty
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Handle accessibility events if needed
        // For this application, we primarily use the service for overlay permissions
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        try {
            if (overlayView?.parent != null) {
                windowManager?.removeView(overlayView?.parent as android.view.View)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing overlay", e)
        }
        
        if (isServiceBound) {
            unbindService(serviceConnection)
        }
        
        Log.d(TAG, "Accessibility service destroyed")
    }
}