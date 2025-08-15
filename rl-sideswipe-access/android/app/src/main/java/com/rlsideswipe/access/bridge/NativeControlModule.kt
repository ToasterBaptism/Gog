package com.rlsideswipe.access.bridge

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.*
import com.rlsideswipe.access.service.ScreenCaptureService

class NativeControlModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    companion object {
        private const val REQUEST_MEDIA_PROJECTION = 1001
        private const val REQUEST_PERMISSIONS = 1002
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
    fun checkPermissions(promise: Promise) {
        try {
            val requiredPermissions = mutableListOf<String>()
            
            // Check notification permission for Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(reactApplicationContext, Manifest.permission.POST_NOTIFICATIONS) 
                    != PackageManager.PERMISSION_GRANTED) {
                    requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            
            // Check system alert window permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(reactApplicationContext)) {
                    requiredPermissions.add("SYSTEM_ALERT_WINDOW")
                }
            }
            
            promise.resolve(requiredPermissions.isEmpty())
        } catch (e: Exception) {
            promise.reject("ERROR", "Failed to check permissions", e)
        }
    }

    @ReactMethod
    fun requestPermissions(promise: Promise) {
        try {
            val activity = currentActivity
            if (activity != null) {
                val requiredPermissions = mutableListOf<String>()
                
                // Check notification permission for Android 13+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(reactApplicationContext, Manifest.permission.POST_NOTIFICATIONS) 
                        != PackageManager.PERMISSION_GRANTED) {
                        requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                
                if (requiredPermissions.isNotEmpty()) {
                    ActivityCompat.requestPermissions(activity, requiredPermissions.toTypedArray(), REQUEST_PERMISSIONS)
                }
                
                // Handle system alert window permission separately
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(reactApplicationContext)) {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    reactApplicationContext.startActivity(intent)
                }
                
                promise.resolve(null)
            } else {
                promise.reject("ERROR", "No current activity")
            }
        } catch (e: Exception) {
            promise.reject("ERROR", "Failed to request permissions", e)
        }
    }

    @ReactMethod
    fun start(promise: Promise) {
        try {
            val activity = currentActivity
            if (activity is com.rlsideswipe.access.MainActivity) {
                activity.requestMediaProjection { captureIntent ->
                    if (captureIntent != null) {
                        val serviceIntent = Intent(reactApplicationContext, ScreenCaptureService::class.java).apply {
                            putExtra("captureIntent", captureIntent)
                        }
                        reactApplicationContext.startForegroundService(serviceIntent)
                        promise.resolve(null)
                    } else {
                        promise.reject("ERROR", "MediaProjection permission denied")
                    }
                }
            } else {
                promise.reject("ERROR", "No current activity or wrong activity type")
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