package com.rlsideswipe.access.bridge

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.provider.Settings
import android.text.TextUtils
import com.facebook.react.bridge.*
import com.rlsideswipe.access.service.ScreenCaptureService

class NativeControlModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    companion object {
        private const val REQUEST_MEDIA_PROJECTION = 1001
    }

    override fun getName(): String = "NativeControlModule"

    @ReactMethod
    fun isServiceEnabled(promise: Promise) {
        try {
            val accessibilityEnabled = Settings.Secure.getInt(
                reactApplicationContext.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
            
            if (accessibilityEnabled == 1) {
                val settingValue = Settings.Secure.getString(
                    reactApplicationContext.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
                
                if (!settingValue.isNullOrEmpty()) {
                    val splitter = TextUtils.SimpleStringSplitter(':')
                    splitter.setString(settingValue)
                    
                    while (splitter.hasNext()) {
                        val accessibilityService = splitter.next()
                        if (accessibilityService.contains("com.rlsideswipe.access/.service.MainAccessibilityService")) {
                            promise.resolve(true)
                            return
                        }
                    }
                }
            }
            promise.resolve(false)
        } catch (e: Exception) {
            promise.reject("ERROR", "Failed to check service status", e)
        }
    }

    @ReactMethod
    fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            reactApplicationContext.startActivity(intent)
        } catch (e: Exception) {
            // Handle error silently
        }
    }

    @ReactMethod
    fun start(promise: Promise) {
        try {
            val activity = currentActivity
            if (activity != null) {
                val mediaProjectionManager = activity.getSystemService(Activity.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
                
                val serviceIntent = Intent(reactApplicationContext, ScreenCaptureService::class.java).apply {
                    putExtra("captureIntent", captureIntent)
                }
                reactApplicationContext.startForegroundService(serviceIntent)
                promise.resolve(null)
            } else {
                promise.reject("ERROR", "No current activity")
            }
        } catch (e: Exception) {
            promise.reject("ERROR", "Failed to start screen capture", e)
        }
    }

    @ReactMethod
    fun stop(promise: Promise) {
        try {
            val serviceIntent = Intent(reactApplicationContext, ScreenCaptureService::class.java)
            reactApplicationContext.stopService(serviceIntent)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("ERROR", "Failed to stop screen capture", e)
        }
    }
}