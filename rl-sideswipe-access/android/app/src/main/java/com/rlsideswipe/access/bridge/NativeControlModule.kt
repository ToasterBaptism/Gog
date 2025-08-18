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
            
            // Check runtime permissions (only those that actually require runtime permission)
            val runtimePermissions = mutableListOf<String>()
            runtimePermissions.add(Manifest.permission.RECORD_AUDIO)
            
            // Install-time permissions (automatically granted if in manifest)
            val installTimePermissions = listOf(
                Manifest.permission.VIBRATE,
                Manifest.permission.WAKE_LOCK,
                Manifest.permission.FOREGROUND_SERVICE
            )
            
            // Check notification permission for Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                runtimePermissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            
            // Check runtime permissions
            for (permission in runtimePermissions) {
                if (ContextCompat.checkSelfPermission(reactApplicationContext, permission) 
                    != PackageManager.PERMISSION_GRANTED) {
                    missingPermissions.add(permission)
                    Log.d("NativeControl", "Missing runtime permission: $permission")
                }
            }
            
            // Check install-time permissions (these should always be granted if in manifest)
            for (permission in installTimePermissions) {
                if (ContextCompat.checkSelfPermission(reactApplicationContext, permission) 
                    != PackageManager.PERMISSION_GRANTED) {
                    missingPermissions.add(permission)
                    Log.w("NativeControl", "Missing install-time permission (check manifest): $permission")
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
                runtimePermissions.add(Manifest.permission.RECORD_AUDIO)
                
                // VIBRATE, WAKE_LOCK, FOREGROUND_SERVICE, and FOREGROUND_SERVICE_MEDIA_PROJECTION 
                // are not runtime permissions - they are granted at install time
                // Only RECORD_AUDIO requires runtime permission request
                
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
            
            // Check runtime permissions (only those that actually require runtime permission)
            val runtimePermissions = listOf(
                Manifest.permission.RECORD_AUDIO
            )
            
            for (permission in runtimePermissions) {
                val granted = ContextCompat.checkSelfPermission(reactApplicationContext, permission) == PackageManager.PERMISSION_GRANTED
                permissionStatus[permission] = granted
                Log.d("NativeControl", "Runtime permission $permission: $granted")
            }
            
            // Check install-time permissions (these are automatically granted if declared in manifest)
            val installTimePermissions = listOf(
                Manifest.permission.VIBRATE,
                Manifest.permission.WAKE_LOCK,
                Manifest.permission.FOREGROUND_SERVICE
            )
            
            for (permission in installTimePermissions) {
                // Install-time permissions are granted if they're in the manifest
                val granted = ContextCompat.checkSelfPermission(reactApplicationContext, permission) == PackageManager.PERMISSION_GRANTED
                permissionStatus[permission] = granted
                Log.d("NativeControl", "Install-time permission $permission: $granted")
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
                Log.d("NativeControl", "MainActivity found, requesting MediaProjection permission...")
                
                // Check if MediaProjectionManager is available
                val mediaProjectionManager = reactApplicationContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
                if (mediaProjectionManager == null) {
                    Log.e("NativeControl", "MediaProjectionManager not available")
                    promise.reject("ERROR", "MediaProjection not supported on this device")
                    return
                }
                
                Log.d("NativeControl", "MediaProjectionManager available, creating screen capture intent...")
                
                activity.requestMediaProjection { captureIntent ->
                    try {
                        if (captureIntent != null) {
                            Log.d("NativeControl", "MediaProjection permission granted, starting service...")
                            Log.d("NativeControl", "Capture intent extras: ${captureIntent.extras}")
                            
                            val serviceIntent = Intent(reactApplicationContext, ScreenCaptureService::class.java).apply {
                                putExtra("captureIntent", captureIntent)
                            }

                            try {
                                val result = reactApplicationContext.startForegroundService(serviceIntent)
                                Log.d("NativeControl", "Service start result: $result")
                                
                                // Give the service a moment to start
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    val isRunning = isScreenCaptureServiceRunning()
                                    Log.d("NativeControl", "Service running after start: $isRunning")
                                }, 1000)
                                
                                promise.resolve(null)
                            } catch (e: Exception) {
                                Log.e("NativeControl", "Failed to start foreground service", e)
                                promise.reject("ERROR", "Failed to start service: ${e.message}", e)
                            }
                        } else {
                            Log.e("NativeControl", "MediaProjection permission denied by user")
                            promise.reject("ERROR", "Screen capture permission denied. Please grant permission to continue.")
                        }
                    } catch (e: Exception) {
                        Log.e("NativeControl", "Error in MediaProjection callback", e)
                        promise.reject("ERROR", "Failed to start service: ${e.message}", e)
                    }
                }
            } else {
                Log.e("NativeControl", "Invalid activity type: $activity (expected MainActivity)")
                Log.e("NativeControl", "Activity class: ${activity?.javaClass?.name}")
                promise.reject("ERROR", "Invalid activity context. Please ensure the app is in the foreground.")
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
                Log.d("NativeControl", "Opening battery optimization settings for package: ${reactApplicationContext.packageName}")
                Log.d("NativeControl", "Android version: ${Build.VERSION.SDK_INT}")
                
                val activity = currentActivity
                if (activity == null) {
                    Log.e("NativeControl", "No current activity available")
                    promise.reject("ERROR", "No current activity available")
                    return
                }
                
                // First try the direct request intent
                try {
                    val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = android.net.Uri.parse("package:${reactApplicationContext.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    
                    // Check if the intent can be resolved
                    val packageManager = reactApplicationContext.packageManager
                    val resolveInfo = packageManager.resolveActivity(intent, 0)
                    if (resolveInfo != null) {
                        Log.d("NativeControl", "Starting battery optimization request intent")
                        activity.startActivity(intent)
                        Log.d("NativeControl", "Successfully opened battery optimization request dialog")
                        promise.resolve(null)
                        return
                    } else {
                        Log.w("NativeControl", "Battery optimization request intent not resolvable")
                    }
                } catch (e: Exception) {
                    Log.w("NativeControl", "Failed to open direct battery optimization request", e)
                }
                
                // Fallback to general ignore battery optimization settings
                try {
                    val intent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    
                    val packageManager = reactApplicationContext.packageManager
                    val resolveInfo = packageManager.resolveActivity(intent, 0)
                    if (resolveInfo != null) {
                        Log.d("NativeControl", "Starting battery optimization settings intent")
                        activity.startActivity(intent)
                        Log.d("NativeControl", "Successfully opened battery optimization settings")
                        promise.resolve(null)
                        return
                    } else {
                        Log.w("NativeControl", "Battery optimization settings intent not resolvable")
                    }
                } catch (e: Exception) {
                    Log.w("NativeControl", "Failed to open battery optimization settings", e)
                }
                
                // Final fallback to application details settings
                try {
                    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.parse("package:${reactApplicationContext.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    
                    val packageManager = reactApplicationContext.packageManager
                    val resolveInfo = packageManager.resolveActivity(intent, 0)
                    if (resolveInfo != null) {
                        Log.d("NativeControl", "Starting application details settings intent")
                        activity.startActivity(intent)
                        Log.d("NativeControl", "Successfully opened application details settings")
                        promise.resolve(null)
                    } else {
                        Log.e("NativeControl", "Application details settings intent not resolvable")
                        promise.reject("ERROR", "No battery settings available on this device")
                    }
                } catch (e: Exception) {
                    Log.e("NativeControl", "All battery optimization intents failed", e)
                    promise.reject("ERROR", "Failed to open battery settings: ${e.message}", e)
                }
            } else {
                Log.d("NativeControl", "Battery optimization not available on Android < M")
                promise.resolve(null)
            }
        } catch (e: Exception) {
            Log.e("NativeControl", "Unexpected error in openBatteryOptimizationSettings", e)
            promise.reject("ERROR", "Failed to open battery settings: ${e.message}", e)
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
    
    @ReactMethod
    fun debugPermissionSystem(promise: Promise) {
        try {
            val debugInfo = mutableMapOf<String, Any>()
            
            // Basic device info
            debugInfo["androidVersion"] = Build.VERSION.SDK_INT
            debugInfo["packageName"] = reactApplicationContext.packageName
            debugInfo["currentActivity"] = currentActivity?.javaClass?.simpleName ?: "null"
            
            // Permission checks
            val permissions = mutableMapOf<String, Boolean>()
            
            // Runtime permissions
            permissions["RECORD_AUDIO"] = ContextCompat.checkSelfPermission(reactApplicationContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions["POST_NOTIFICATIONS"] = ContextCompat.checkSelfPermission(reactApplicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            }
            
            // Install-time permissions
            permissions["VIBRATE"] = ContextCompat.checkSelfPermission(reactApplicationContext, Manifest.permission.VIBRATE) == PackageManager.PERMISSION_GRANTED
            permissions["WAKE_LOCK"] = ContextCompat.checkSelfPermission(reactApplicationContext, Manifest.permission.WAKE_LOCK) == PackageManager.PERMISSION_GRANTED
            permissions["FOREGROUND_SERVICE"] = ContextCompat.checkSelfPermission(reactApplicationContext, Manifest.permission.FOREGROUND_SERVICE) == PackageManager.PERMISSION_GRANTED
            permissions["FOREGROUND_SERVICE_MEDIA_PROJECTION"] = ContextCompat.checkSelfPermission(reactApplicationContext, Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION) == PackageManager.PERMISSION_GRANTED
            permissions["REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"] = ContextCompat.checkSelfPermission(reactApplicationContext, Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) == PackageManager.PERMISSION_GRANTED
            
            // Special permissions
            permissions["SYSTEM_ALERT_WINDOW"] = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(reactApplicationContext)
            } else {
                true
            }
            
            permissions["BATTERY_OPTIMIZATION_IGNORED"] = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = reactApplicationContext.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                powerManager.isIgnoringBatteryOptimizations(reactApplicationContext.packageName)
            } else {
                true
            }
            
            permissions["ACCESSIBILITY_SERVICE"] = isAccessibilityServiceEnabled()
            
            debugInfo["permissions"] = permissions
            
            // Service status
            debugInfo["screenCaptureServiceRunning"] = isScreenCaptureServiceRunning()
            debugInfo["accessibilityServiceRunning"] = com.rlsideswipe.access.service.MainAccessibilityService.isRunning()
            
            // MediaProjection availability
            val mediaProjectionManager = reactApplicationContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
            debugInfo["mediaProjectionAvailable"] = mediaProjectionManager != null
            
            Log.d("NativeControl", "Debug info: $debugInfo")
            promise.resolve(Arguments.makeNativeMap(debugInfo as Map<String, Any>))
        } catch (e: Exception) {
            Log.e("NativeControl", "Failed to get debug info", e)
            promise.reject("ERROR", "Failed to get debug info: ${e.message}", e)
        }
    }
}