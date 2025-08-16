package com.rlsideswipe.access.bridge

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
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
            
            Log.d("NativeControl", "Accessibility enabled: $accessibilityEnabled")
            
            if (accessibilityEnabled == 1) {
                val settingValue = Settings.Secure.getString(
                    reactApplicationContext.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
                
                Log.d("NativeControl", "Enabled services: $settingValue")
                
                if (!settingValue.isNullOrEmpty()) {
                    val splitter = TextUtils.SimpleStringSplitter(':')
                    splitter.setString(settingValue)
                    
                    while (splitter.hasNext()) {
                        val accessibilityService = splitter.next()
                        Log.d("NativeControl", "Checking service: $accessibilityService")
                        
                        // Check for multiple possible formats
                        if (accessibilityService.contains("com.rlsideswipe.access/.service.MainAccessibilityService") ||
                            accessibilityService.contains("com.rlsideswipe.access/com.rlsideswipe.access.service.MainAccessibilityService") ||
                            (accessibilityService.contains("com.rlsideswipe.access") && accessibilityService.contains("MainAccessibilityService"))) {
                            Log.d("NativeControl", "Service found: $accessibilityService")
                            promise.resolve(true)
                            return
                        }
                    }
                }
            }
            Log.d("NativeControl", "Service not enabled")
            promise.resolve(false)
        } catch (e: Exception) {
            Log.e("NativeControl", "Failed to check service status", e)
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
            val missingPermissions = mutableListOf<String>()
            
            // Check runtime permissions
            val runtimePermissions = mutableListOf<String>()
            
            // Always check these permissions
            runtimePermissions.add(Manifest.permission.VIBRATE)
            runtimePermissions.add(Manifest.permission.RECORD_AUDIO)
            runtimePermissions.add(Manifest.permission.WAKE_LOCK)
            runtimePermissions.add(Manifest.permission.FOREGROUND_SERVICE)
            runtimePermissions.add(Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION)
            
            // Check notification permission for Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                runtimePermissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            
            // Check all runtime permissions
            for (permission in runtimePermissions) {
                if (ContextCompat.checkSelfPermission(reactApplicationContext, permission) 
                    != PackageManager.PERMISSION_GRANTED) {
                    missingPermissions.add(permission)
                    Log.d("NativeControl", "Missing permission: $permission")
                }
            }
            
            // Check system alert window permission separately
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(reactApplicationContext)) {
                    missingPermissions.add("SYSTEM_ALERT_WINDOW")
                    Log.d("NativeControl", "Missing permission: SYSTEM_ALERT_WINDOW")
                }
            }
            
            Log.d("NativeControl", "Permission check complete. Missing: ${missingPermissions.size}")
            promise.resolve(missingPermissions.isEmpty())
        } catch (e: Exception) {
            Log.e("NativeControl", "Failed to check permissions", e)
            promise.reject("ERROR", "Failed to check permissions", e)
        }
    }

    @ReactMethod
    fun requestPermissions(promise: Promise) {
        try {
            val activity = currentActivity
            if (activity != null) {
                val requiredPermissions = mutableListOf<String>()
                
                // Check all runtime permissions
                val runtimePermissions = mutableListOf<String>()
                runtimePermissions.add(Manifest.permission.VIBRATE)
                runtimePermissions.add(Manifest.permission.RECORD_AUDIO)
                runtimePermissions.add(Manifest.permission.WAKE_LOCK)
                runtimePermissions.add(Manifest.permission.FOREGROUND_SERVICE)
                runtimePermissions.add(Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION)
                
                // Check notification permission for Android 13+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    runtimePermissions.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                
                // Add missing runtime permissions to request list
                for (permission in runtimePermissions) {
                    if (ContextCompat.checkSelfPermission(reactApplicationContext, permission) 
                        != PackageManager.PERMISSION_GRANTED) {
                        requiredPermissions.add(permission)
                        Log.d("NativeControl", "Requesting permission: $permission")
                    }
                }
                
                // Request runtime permissions if any are missing
                if (requiredPermissions.isNotEmpty()) {
                    Log.d("NativeControl", "Requesting ${requiredPermissions.size} runtime permissions")
                    ActivityCompat.requestPermissions(activity, requiredPermissions.toTypedArray(), REQUEST_PERMISSIONS)
                } else {
                    Log.d("NativeControl", "All runtime permissions already granted")
                }
                
                // Handle system alert window permission separately
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(reactApplicationContext)) {
                    Log.d("NativeControl", "Requesting overlay permission")
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        data = android.net.Uri.parse("package:${reactApplicationContext.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    reactApplicationContext.startActivity(intent)
                } else {
                    Log.d("NativeControl", "Overlay permission already granted")
                }
                
                promise.resolve(null)
            } else {
                promise.reject("ERROR", "No current activity")
            }
        } catch (e: Exception) {
            Log.e("NativeControl", "Failed to request permissions", e)
            promise.reject("ERROR", "Failed to request permissions", e)
        }
    }

    @ReactMethod
    fun getDetailedPermissionStatus(promise: Promise) {
        try {
            val permissionStatus = mutableMapOf<String, Boolean>()
            
            // Check runtime permissions
            val runtimePermissions = listOf(
                Manifest.permission.VIBRATE,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WAKE_LOCK,
                Manifest.permission.FOREGROUND_SERVICE,
                Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION
            )
            
            for (permission in runtimePermissions) {
                val granted = ContextCompat.checkSelfPermission(reactApplicationContext, permission) == PackageManager.PERMISSION_GRANTED
                permissionStatus[permission] = granted
                Log.d("NativeControl", "Permission $permission: $granted")
            }
            
            // Check notification permission for Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val notificationGranted = ContextCompat.checkSelfPermission(reactApplicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                permissionStatus[Manifest.permission.POST_NOTIFICATIONS] = notificationGranted
                Log.d("NativeControl", "Permission POST_NOTIFICATIONS: $notificationGranted")
            }
            
            // Check system alert window permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val overlayGranted = Settings.canDrawOverlays(reactApplicationContext)
                permissionStatus["SYSTEM_ALERT_WINDOW"] = overlayGranted
                Log.d("NativeControl", "Permission SYSTEM_ALERT_WINDOW: $overlayGranted")
            }
            
            // Check accessibility service
            val accessibilityEnabled = isAccessibilityServiceEnabled()
            permissionStatus["ACCESSIBILITY_SERVICE"] = accessibilityEnabled
            Log.d("NativeControl", "Accessibility service enabled: $accessibilityEnabled")
            
            // Check battery optimization
            val batteryOptimized = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = reactApplicationContext.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                powerManager.isIgnoringBatteryOptimizations(reactApplicationContext.packageName)
            } else {
                true
            }
            permissionStatus["BATTERY_OPTIMIZATION_IGNORED"] = batteryOptimized
            Log.d("NativeControl", "Battery optimization ignored: $batteryOptimized")
            
            promise.resolve(Arguments.makeNativeMap(permissionStatus as Map<String, Any>))
        } catch (e: Exception) {
            Log.e("NativeControl", "Failed to get detailed permission status", e)
            promise.reject("ERROR", "Failed to get detailed permission status", e)
        }
    }
    
    private fun isAccessibilityServiceEnabled(): Boolean {
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
                        if (accessibilityService.contains("com.rlsideswipe.access") && 
                            accessibilityService.contains("MainAccessibilityService")) {
                            return true
                        }
                    }
                }
            }
            return false
        } catch (e: Exception) {
            Log.e("NativeControl", "Failed to check accessibility service", e)
            return false
        }
    }

    @ReactMethod
    fun hasMediaProjectionPermission(promise: Promise) {
        try {
            // MediaProjection permission can't be checked directly
            // However, we can check if the service is currently running
            val isServiceRunning = isScreenCaptureServiceRunning()
            Log.d("NativeControl", "ScreenCaptureService running: $isServiceRunning")
            promise.resolve(isServiceRunning)
        } catch (e: Exception) {
            Log.e("NativeControl", "Failed to check MediaProjection permission", e)
            promise.reject("ERROR", "Failed to check MediaProjection permission", e)
        }
    }
    
    private fun isScreenCaptureServiceRunning(): Boolean {
        try {
            val activityManager = reactApplicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            for (service in activityManager.getRunningServices(Integer.MAX_VALUE)) {
                if (ScreenCaptureService::class.java.name == service.service.className) {
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e("NativeControl", "Error checking service status", e)
        }
        return false
    }

    @ReactMethod
    fun start(promise: Promise) {
        try {
            Log.d("NativeControl", "Starting screen capture...")
            val activity = currentActivity
            Log.d("NativeControl", "Current activity: $activity")
            
            if (activity is com.rlsideswipe.access.MainActivity) {
                Log.d("NativeControl", "Requesting MediaProjection permission...")
                activity.requestMediaProjection { captureIntent ->
                    try {
                        if (captureIntent != null) {
                            Log.d("NativeControl", "MediaProjection permission granted, starting service...")
                            val serviceIntent = Intent(reactApplicationContext, ScreenCaptureService::class.java).apply {
                                putExtra("captureIntent", captureIntent)
                            }
                            
                            val result = reactApplicationContext.startForegroundService(serviceIntent)
                            Log.d("NativeControl", "Service start result: $result")
                            promise.resolve(null)
                        } else {
                            Log.e("NativeControl", "MediaProjection permission denied")
                            promise.reject("ERROR", "MediaProjection permission denied")
                        }
                    } catch (e: Exception) {
                        Log.e("NativeControl", "Error in MediaProjection callback", e)
                        promise.reject("ERROR", "Failed to start service: ${e.message}", e)
                    }
                }
            } else {
                Log.e("NativeControl", "Invalid activity type: $activity")
                promise.reject("ERROR", "No current activity or wrong activity type")
            }
        } catch (e: Exception) {
            Log.e("NativeControl", "Failed to start screen capture", e)
            promise.reject("ERROR", "Failed to start screen capture: ${e.message}", e)
        }
    }

    @ReactMethod
    fun stop(promise: Promise) {
        try {
            Log.d("NativeControl", "Stopping screen capture service...")
            val serviceIntent = Intent(reactApplicationContext, ScreenCaptureService::class.java)
            val result = reactApplicationContext.stopService(serviceIntent)
            Log.d("NativeControl", "Stop service result: $result")
            promise.resolve(null)
        } catch (e: Exception) {
            Log.e("NativeControl", "Failed to stop screen capture", e)
            promise.reject("ERROR", "Failed to stop screen capture: ${e.message}", e)
        }
    }
    
    @ReactMethod
    fun checkBatteryOptimization(promise: Promise) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = reactApplicationContext.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                val packageName = reactApplicationContext.packageName
                val isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(packageName)
                
                Log.d("NativeControl", "Battery optimization ignored: $isIgnoringBatteryOptimizations")
                promise.resolve(isIgnoringBatteryOptimizations)
            } else {
                // Battery optimization not available on older versions
                promise.resolve(true)
            }
        } catch (e: Exception) {
            Log.e("NativeControl", "Failed to check battery optimization", e)
            promise.reject("ERROR", "Failed to check battery optimization: ${e.message}", e)
        }
    }
    
    @ReactMethod
    fun openBatteryOptimizationSettings(promise: Promise) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = android.net.Uri.parse("package:${reactApplicationContext.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                reactApplicationContext.startActivity(intent)
                promise.resolve(null)
            } else {
                promise.resolve(null)
            }
        } catch (e: Exception) {
            Log.e("NativeControl", "Failed to open battery optimization settings", e)
            // Fallback to general battery settings
            try {
                val intent = Intent(android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                reactApplicationContext.startActivity(intent)
                promise.resolve(null)
            } catch (e2: Exception) {
                promise.reject("ERROR", "Failed to open battery settings: ${e2.message}", e2)
            }
        }
    }
    
    @ReactMethod
    fun isAccessibilityServiceActuallyRunning(promise: Promise) {
        try {
            val isRunning = com.rlsideswipe.access.service.MainAccessibilityService.isRunning()
            Log.d("NativeControl", "Accessibility service actually running: $isRunning")
            promise.resolve(isRunning)
        } catch (e: Exception) {
            Log.e("NativeControl", "Failed to check if accessibility service is running", e)
            promise.reject("ERROR", "Failed to check accessibility service status: ${e.message}", e)
        }
    }
}