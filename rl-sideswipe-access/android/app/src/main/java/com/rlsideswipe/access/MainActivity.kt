package com.rlsideswipe.access

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate
import com.rlsideswipe.access.service.ScreenCaptureService

class MainActivity : ReactActivity() {

    private var pendingMediaProjectionResult: ((Intent?) -> Unit)? = null
    private lateinit var mediaProjectionLauncher: ActivityResultLauncher<Intent>

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
        Log.d("MainActivity", "Requesting MediaProjection permission...")
        pendingMediaProjectionResult = callback
        
        try {
            val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
            if (mediaProjectionManager == null) {
                Log.e("MainActivity", "MediaProjectionManager is null")
                callback(null)
                return
            }
            
            val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
            Log.d("MainActivity", "Created screen capture intent: $captureIntent")
            Log.d("MainActivity", "Intent action: ${captureIntent.action}")
            Log.d("MainActivity", "Intent component: ${captureIntent.component}")
            
            // Add timeout to detect if dialog never appears
            val timeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
            val timeoutRunnable = Runnable {
                if (pendingMediaProjectionResult != null) {
                    Log.w("MainActivity", "MediaProjection dialog timeout - no response after 30 seconds")
                    val callback = pendingMediaProjectionResult
                    pendingMediaProjectionResult = null
                    callback?.invoke(null)
                }
            }
            timeoutHandler.postDelayed(timeoutRunnable, 30000) // 30 second timeout
            
            Log.d("MainActivity", "Launching MediaProjection intent...")
            mediaProjectionLauncher.launch(captureIntent)
            
            // Cancel timeout if we get a response
            val originalCallback = callback
            pendingMediaProjectionResult = { result ->
                timeoutHandler.removeCallbacks(timeoutRunnable)
                originalCallback(result)
            }
            
        } catch (e: Exception) {
            Log.e("MainActivity", "Exception in requestMediaProjection", e)
            callback(null)
        }
    }
}