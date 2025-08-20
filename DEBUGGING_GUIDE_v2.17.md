# 🔍 RL Sideswipe Access v2.17 ENHANCED - Debugging Guide

## 📱 APK Downloads

### 🌐 Download Links
- **Debug APK (98MB)**: https://work-2-geeubuwrjouvyonf.prod-runtime.all-hands.dev/app-debug.apk
- **Release APK (49MB)**: https://work-2-geeubuwrjouvyonf.prod-runtime.all-hands.dev/app-release.apk
- **Download Page**: https://work-2-geeubuwrjouvyonf.prod-runtime.all-hands.dev/

### 📋 Version Information
- **Version**: v2.17 ENHANCED with Critical Overlay Fixes
- **Build Date**: August 20, 2025
- **Target**: Android 7.0+ (API 24+)
- **Key Fixes**: PredictionOverlayService startup, foreground service management, overlay visibility

## 🔧 Critical Fixes Implemented

### 🚀 Service Reliability
- **PredictionOverlayService** now runs as foreground service with notification
- **Service restart policy** set to START_STICKY (prevents termination)
- **Proper notification channel** creation for Android O+
- **Enhanced service startup** sequence with initialization delay

### 🎯 Overlay Improvements
- **Always-visible test elements** for debugging (magenta rectangle, screen info)
- **Enhanced logging** throughout overlay creation and drawing
- **Improved coordinate transformation** debugging
- **Better visual feedback** with different colors for different point types

### ⏱️ Startup Timing
- **500ms delay** after starting PredictionOverlayService before sending data
- **Immediate test points** on startup to verify overlay functionality
- **Enhanced service initialization** sequence

## 🔍 Logcat Debugging Commands

### 📱 Basic Service Monitoring
```bash
# Monitor main services
adb logcat | grep "ScreenCaptureService\|PredictionOverlay"

# Service lifecycle tracking
adb logcat | grep -E "Service|onCreate|onStartCommand|onDestroy"
```

### 🎯 Ball Detection Debugging
```bash
# Track ball detection and template matching
adb logcat | grep -E "🎯|🔍|DETECTION|TEMPLATE|BALL"

# Template matching details
adb logcat | grep -E "TEMPLATE_MATCH|CONFIDENCE|THRESHOLD"
```

### 🎨 Overlay System Debugging
```bash
# Monitor overlay rendering and updates
adb logcat | grep -E "🎨|OVERLAY|onDraw|invalidate"

# Coordinate transformation debugging
adb logcat | grep -E "COORDINATE|TRANSFORM|SCALE"
```

### 📊 Performance Monitoring
```bash
# Monitor processing performance and frame rates
adb logcat | grep -E "PERFORMANCE|FPS|PROCESSING"

# Memory and resource usage
adb logcat | grep -E "MEMORY|RESOURCE|ALLOCATION"
```

### 🔧 Complete Debug Filtering
```bash
# Filter for all relevant app components
adb logcat -s ScreenCaptureService PredictionOverlay PredictionOverlayView BallTemplateManager

# Save filtered logs to file for analysis
adb logcat | grep "ScreenCaptureService\|PredictionOverlay" > debug.log
```

### 🎮 Rocket League Specific
```bash
# Monitor Rocket League detection and interaction
adb logcat | grep -E "Rocket League|RL|SIDESWIPE"

# App state changes
adb logcat | grep -E "FOREGROUND|BACKGROUND|APP_STATE"
```

## 🔍 What to Look For

### ✅ Successful Startup Indicators
- `"PredictionOverlayService created"` - Service initialization
- `"ScreenCaptureService created"` - Screen capture ready
- `"Drew test rectangle"` - Overlay rendering working
- `"onDraw() called"` - View updates happening
- `"Foreground service started"` - Service won't be killed

### 🎯 Ball Detection Success
- `"TEMPLATE MATCHES FOUND"` - Ball detected
- `"Ball indicator: original(...) -> transformed(...)"` - Coordinate mapping
- `"🎯 Ball detection confidence: X%"` - Detection strength

### ❌ Common Issues to Check
- `"NO TEMPLATE MATCHES"` - No balls detected (normal when no balls visible)
- `"Permission denied"` - Overlay permission not granted
- `"Service destroyed"` - Service being killed (should restart automatically)
- `"View not ready"` - Overlay view initialization issues

## 🚨 Expected Behavior After Fix

### 📱 Immediate After "Start Now"
1. **Two notifications appear**:
   - "Monitoring for Rocket League"
   - "Ball Prediction Overlay"

2. **Visual overlay elements**:
   - Magenta test rectangle in top-left corner (50,50)-(200,200)
   - Screen dimensions text at bottom
   - Point count display

3. **Test points visible**:
   - Yellow test points at 10%, 50%, and 90% of screen dimensions
   - Different colors for different point types

### 🎮 During Ball Detection
- **Red circles** appear where balls are detected
- **Prediction paths** drawn in gradient colors (red→yellow→green)
- **Velocity arrows** showing ball direction
- **Test points remain visible** for reference

### 🔄 When Leaving App
- **Both services continue running** (foreground services)
- **Overlay remains visible** on screen
- **Services do not terminate** when app goes to background
- **Notifications persist** in status bar

## 🛠️ Installation Instructions

### 📲 APK Installation
1. **Enable Unknown Sources**: Settings → Security → Install from Unknown Sources
2. **Download APK**: Use debug version for troubleshooting
3. **Install**: Tap APK file and follow prompts

### 🔐 Required Permissions
1. **Screen Capture**: Allow when prompted
2. **Overlay Permission**: Settings → Apps → Special Access → Display over other apps
3. **Battery Optimization**: Disable for the app to prevent service killing

### 🧪 Testing Steps
1. **Install and launch** the app
2. **Grant all permissions** when prompted
3. **Tap "Start Now"** button
4. **Look for notifications** and overlay elements
5. **Check logcat** for startup messages
6. **Test with Rocket League** if available

## 🐛 Troubleshooting

### 🔴 No Overlay Visible
```bash
# Check overlay creation
adb logcat | grep -E "OVERLAY|WindowManager|addView"

# Check permissions
adb logcat | grep -E "PERMISSION|OVERLAY_PERMISSION"
```

### 🔴 Service Stops When App Closed
```bash
# Check foreground service status
adb logcat | grep -E "FOREGROUND|NOTIFICATION|SERVICE_STICKY"

# Check for service destruction
adb logcat | grep -E "onDestroy|Service.*destroyed"
```

### 🔴 No Ball Detection
```bash
# Check template loading
adb logcat | grep -E "TEMPLATE|LOAD|ASSET"

# Check detection processing
adb logcat | grep -E "DETECTION|PROCESSING|FRAME"
```

## 📊 Performance Expectations

### 🎯 Normal Operation
- **FPS**: 15-30 frames per second
- **Detection Latency**: <100ms
- **Memory Usage**: 50-100MB
- **CPU Usage**: 10-20% on modern devices

### ⚠️ Performance Issues
- **Low FPS** (<10): Check device performance, reduce detection frequency
- **High Memory** (>200MB): Possible memory leak, restart service
- **High CPU** (>30%): Optimize detection parameters

## 📝 Log Analysis Tips

### 🔍 Reading Timestamps
- Look for **consistent timing** between detection cycles
- **Gaps in logs** may indicate service interruption
- **Rapid repeated messages** may indicate loops or errors

### 🎯 Detection Quality
- **Confidence scores** should be >65% for reliable detection
- **Coordinate values** should be within screen bounds
- **Template matches** should correlate with visible balls

### 🚀 Service Health
- **Regular heartbeat** messages indicate healthy operation
- **Foreground service** notifications should persist
- **Memory warnings** indicate potential issues

---

## 📞 Support Information

If issues persist after following this guide:
1. **Collect logs** using the commands above
2. **Note device model** and Android version
3. **Describe specific behavior** observed
4. **Include logcat output** for analysis

**Build Information**: v2.17 ENHANCED (August 20, 2025)
**Commit**: 0256738 - CRITICAL FIX: Resolve PredictionOverlayService startup and visibility issues