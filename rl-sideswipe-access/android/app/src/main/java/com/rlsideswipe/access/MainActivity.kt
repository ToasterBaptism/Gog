package com.rlsideswipe.access

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate
import com.rlsideswipe.access.service.ScreenCaptureService

class MainActivity : ReactActivity() {

    companion object {
        const val REQUEST_MEDIA_PROJECTION = 1001
        var pendingMediaProjectionResult: ((Intent?) -> Unit)? = null
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
    }

    fun requestMediaProjection(callback: (Intent?) -> Unit) {
        pendingMediaProjectionResult = callback
        val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
        startActivityForResult(captureIntent, REQUEST_MEDIA_PROJECTION)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            REQUEST_MEDIA_PROJECTION -> {
                val callback = pendingMediaProjectionResult
                pendingMediaProjectionResult = null
                
                if (resultCode == Activity.RESULT_OK && data != null) {
                    callback?.invoke(data)
                } else {
                    callback?.invoke(null)
                }
            }
        }
    }
}