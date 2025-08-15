# Accessibility Service Troubleshooting Guide

## 🔧 Accessibility Service Stability Issues

This guide addresses the common issue where the accessibility service keeps turning off or doesn't work properly after being enabled.

## 📱 Latest Fixes (v2.4 - 2025-08-15 20:09 UTC)

### Major Improvements:
- **Service Configuration**: Restricted to specific packages and reduced permissions
- **Battery Optimization**: Added detection and settings to prevent system kills
- **Real-time Monitoring**: Detect when service is enabled but not actually running
- **Enhanced Recovery**: Better handling of service interruptions and automatic recovery

## 🛠️ Step-by-Step Troubleshooting

### 1. Check Service Status
The app now shows detailed status information:
- ✅ **"Ready to start"** - All good, service is running
- ⚠️ **"Accessibility Service Not Running"** - Service enabled but killed by system
- ❌ **"Needs Accessibility Service"** - Service not enabled in settings
- ⚠️ **"Battery Optimization Enabled"** - System may kill the service

### 2. Complete Setup Process
The app now has a 3-step setup process:

#### Step 1: Enable Accessibility Service
1. Tap "Open Settings" in the app
2. Find "RL Sideswipe Access" in the accessibility services list
3. Toggle it ON
4. Confirm the warning dialog

#### Step 2: Grant App Permissions
1. Tap "Grant Permissions" in the app
2. Allow all requested permissions:
   - Microphone (for audio feedback)
   - Vibration (for haptic feedback)
   - Notifications (for service status)
   - Display over other apps (for overlay)

#### Step 3: Disable Battery Optimization
1. Tap "Open Settings" for battery optimization
2. Find "RL Sideswipe Access" in the list
3. Select "Don't optimize" or "Allow"
4. This prevents Android from killing the service

### 3. Verify Service is Actually Running
After completing all steps:
- The app should show "Ready to start"
- All three steps should have green checkmarks
- No warning messages should appear

## 🔋 Battery Optimization Issues

### Why This Matters:
Android's battery optimization can kill accessibility services even when they're enabled in settings. This is the most common cause of the service "turning off."

### How to Fix:
1. **Automatic**: Use the app's "Disable Battery Optimization" button
2. **Manual**: Go to Settings → Battery → Battery Optimization → Find "RL Sideswipe Access" → Don't optimize

### Device-Specific Instructions:

#### Samsung Devices:
- Settings → Device care → Battery → More battery settings → Optimize battery usage
- Change filter to "All apps" → Find "RL Sideswipe Access" → Toggle OFF

#### Xiaomi/MIUI:
- Settings → Apps → Manage apps → RL Sideswipe Access → Battery saver → No restrictions
- Also: Settings → Battery & performance → Manage apps' battery usage → Choose apps → RL Sideswipe Access → No restrictions

#### OnePlus/OxygenOS:
- Settings → Battery → Battery optimization → All apps → RL Sideswipe Access → Don't optimize

#### Huawei/EMUI:
- Settings → Battery → App launch → RL Sideswipe Access → Manage manually → Enable all toggles

## 🔍 Advanced Troubleshooting

### Service Keeps Getting Disabled:
1. **Check for conflicting accessibility services** - Disable other accessibility apps temporarily
2. **Restart device** after enabling the service
3. **Check Android version** - Some Android 12+ versions are more aggressive
4. **Developer options** - Enable "Don't keep activities" OFF if enabled

### Service Enabled But Not Working:
1. **Force stop the app** and restart it
2. **Clear app cache** (not data) in Android settings
3. **Check logcat** for error messages (for developers)
4. **Reinstall the app** if issues persist

### Permission Issues:
1. **Grant all permissions** - The app needs all requested permissions to work
2. **Check overlay permission** - Must be enabled for the visual overlay
3. **Notification permission** - Required on Android 13+ for service status

## 📊 Logging and Debugging

### For Users:
The app now provides better error messages and status information. Check the main screen for specific issues.

### For Developers:
Enhanced logging throughout the service lifecycle:
```bash
adb logcat | grep -E "(MainAccessibilityService|NativeControl|ScreenCapture)"
```

Key log messages to look for:
- `Accessibility service connected successfully`
- `Service instance set, service is now running`
- `Accessibility service interrupted - attempting to recover`
- `Battery optimization ignored: true/false`

## 🎯 Prevention Tips

### To Keep Service Running:
1. **Always disable battery optimization** for the app
2. **Don't use aggressive battery savers** or task killers
3. **Keep the app in recent apps** (don't swipe it away)
4. **Restart the device** occasionally to refresh system services

### Best Practices:
1. Complete all 3 setup steps before using
2. Check service status if the app stops working
3. Restart the app if you see warning messages
4. Update to the latest version for bug fixes

## 🆘 Still Having Issues?

If the accessibility service still keeps turning off after following this guide:

1. **Check your Android version** - Some versions have stricter policies
2. **Try a different device** - Some manufacturers are more restrictive
3. **Report the issue** with:
   - Device model and Android version
   - Exact steps that reproduce the problem
   - Screenshots of the permission overlay
   - Logcat output if possible

## 📝 Technical Details

### Service Configuration Changes (v2.4):
- **Package restriction**: Only monitors Rocket League Sideswipe and own app
- **Reduced permissions**: Uses `flagDefault` instead of complex flag combinations
- **Feedback type**: Changed to `feedbackGeneric` to avoid conflicts
- **Event filtering**: Only essential window events to reduce system load

### Recovery Mechanisms:
- **Interrupt handling**: Automatic overlay restoration after interruptions
- **Lifecycle management**: Proper cleanup and re-initialization
- **Status monitoring**: Real-time detection of service health
- **Error recovery**: Graceful handling of service failures

This comprehensive approach should resolve most accessibility service stability issues.