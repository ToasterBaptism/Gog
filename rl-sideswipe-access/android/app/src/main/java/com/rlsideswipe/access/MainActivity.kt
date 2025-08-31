package com.rlsideswipe.access

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate
import com.rlsideswipe.access.service.ScreenCaptureService
import com.rlsideswipe.access.bridge.NativeControlModule

class MainActivity : ReactActivity() {

    private var pendingMediaProjectionResult: ((Intent?) -> Unit)? = null
    private lateinit var mediaProjectionLauncher: ActivityResultLauncher<Intent>
    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val timeoutRunnable = Runnable {
        Log.w("MainActivity", "MediaProjection permission request timed out")
        pendingMediaProjectionResult?.invoke(null)
        pendingMediaProjectionResult = null
    }

    /**
     * Returns the name of the main component registered from JavaScript. This is used to schedule
     * rendering of the component.
     */
    override fun getMainComponentName(): String = "RLSideswipeAccess"

    /**
     * Returns the instance of the [ReactActivityDelegate]. We use [DefaultReactActivityDelegate]
     * which allows you to enable New Architecture with a single boolean flags [fabricEnabled]
     */
    override fun createReactActivityDelegate(): ReactActivityDelegate =
        DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize the activity result launcher
        mediaProjectionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            Log.d("MainActivity", "MediaProjection result: ${result.resultCode}")
            val callback = pendingMediaProjectionResult
            timeoutHandler.removeCallbacks(timeoutRunnable)
            pendingMediaProjectionResult = null
            
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                Log.d("MainActivity", "MediaProjection permission granted")
                callback?.invoke(result.data)
            } else {
                Log.d("MainActivity", "MediaProjection permission denied")
                callback?.invoke(null)
            }
        }
    }

    fun requestMediaProjection(callback: (Intent?) -> Unit) {
        Log.d("MainActivity", "=== REQUESTING MEDIA PROJECTION PERMISSION ===")
        Log.d("MainActivity", "Callback provided: ${callback != null}")
        
        try {
            // Clear any existing pending callback first
            pendingMediaProjectionResult = null
            Log.d("MainActivity", "Cleared existing pending callback")
            
            val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
            if (mediaProjectionManager == null) {
                Log.e("MainActivity", "MediaProjectionManager is null - cannot proceed")
                callback(null)
                return
            }
            
            val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
            Log.d("MainActivity", "Created screen capture intent: $captureIntent")
            
            // Set up the callback with enhanced logging and timeout handling
            pendingMediaProjectionResult = { result ->
                Log.d("MainActivity", "=== MEDIA PROJECTION CALLBACK INVOKED ===")
                Log.d("MainActivity", "MediaProjection callback invoked with result: $result")
                Log.d("MainActivity", "Result is null: ${result == null}")
                // Cancel timeout when we get a result
                timeoutHandler.removeCallbacks(timeoutRunnable)
                // Invoke the original callback
                callback(result)
                Log.d("MainActivity", "Original callback invoked successfully")
            }
            // Start timeout to guard against no activity result
            timeoutHandler.postDelayed(timeoutRunnable, 30000)
            
            Log.d("MainActivity", "Callback set up successfully, launching MediaProjection intent...")
            // Ensure launcher is invoked on the UI thread to avoid IllegalStateException
            runOnUiThread {
                mediaProjectionLauncher.launch(captureIntent)
                Log.d("MainActivity", "MediaProjection intent launched successfully")
            }
            
        } catch (e: Exception) {
            Log.e("MainActivity", "Exception in requestMediaProjection: ${e.message}", e)
            pendingMediaProjectionResult = null
            callback(null)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        // Forward permission results to NativeControlModule
        try {
            val reactInstanceManager = reactNativeHost.reactInstanceManager
            val reactContext = reactInstanceManager.currentReactContext
            if (reactContext != null) {
                val nativeModule = reactContext.getNativeModule(NativeControlModule::class.java)
                nativeModule?.onPermissionResult(requestCode, permissions, grantResults)
            } else {
                Log.w("MainActivity", "React context not available for permission result")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to forward permission result to NativeControlModule", e)
        }
    }
}