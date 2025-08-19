# RL Sideswipe Access - Version History

## v2.7-LANDSCAPE-COORDINATE-FIX (Current)
**Release Date:** 2025-08-19  
**APK:** `app-release-v2.7-LANDSCAPE-COORDINATE-FIX.apk`  
**Size:** 49MB  

### 🔧 Major Fixes
- **Landscape Mode Support:** Fixed coordinate system mismatch for horizontal gameplay
- **Ball Position Accuracy:** Magenta indicator now appears on actual ball location
- **Prediction Alignment:** Trajectory lines follow proper ball path instead of flat lines
- **Search Area Optimization:** Focused detection for landscape layout (avoids UI elements)

### 🎯 Technical Implementation
- Added orientation detection using WindowManager and display rotation
- Implemented coordinate transformation between detection and overlay systems
- Modified search areas: landscape uses center 80%, portrait uses upper 75%
- Added screen info passing from ScreenCaptureService to PredictionOverlayService
- Enhanced coordinate scaling and transformation in overlay rendering

### 🐛 Root Cause Identified & Fixed
The "flat horizontal line" issue was caused by coordinate system mismatch:
- Game runs in landscape mode (horizontal)
- Detection system was designed for portrait orientation
- Ball detected in upper-middle area appeared as overlay in bottom-left corner
- **FIXED** by proper coordinate transformation and orientation detection

---

## v2.6-DEBUG-VISUAL-INDICATOR (Previous Debug)
**Release Date:** 2025-08-19  
**APK:** `app-release-v2.6-DEBUG-VISUAL-INDICATOR.apk`  
**Size:** 49MB  

### 🐛 Debug Features
- **Visual Ball Position Indicator:** Bright magenta circle shows exactly where ball is detected
- **Crosshair Marker:** Precise detection coordinates with crosshair overlay
- **Enhanced Logging:** Comprehensive position, velocity, and detection region logging
- **Focused Detection Area:** Search limited to upper 75% of screen (avoid UI elements)
- **Smaller Grid Search:** 30px grid for better precision
- **Relaxed Validation:** Lower thresholds for debugging (2+ positions, 10+ pixels, 10+ px/s)

### 🎯 Purpose
This debug version helped identify the root cause of the "flat horizontal line" issue by showing exactly where the ball was being detected versus where the prediction overlay appeared.

---

## v2.5-IMPROVED-DETECTION
**Release Date:** 2025-08-19  
**APK:** `app-release-v2.5-IMPROVED-DETECTION.apk`  
**Size:** 49MB  

### 🔧 Improvements
- **Enhanced Gray Ball Detection:** Broader color ranges and improved variance tolerance
- **Dark Metallic Ball Detection:** For shadowed areas (brightness 50-120, variance <30)
- **Better Prediction Validation:** Minimum 3 ball positions and 20+ detected pixels required
- **Speed Threshold:** Minimum 50px/s to avoid showing predictions for detection noise
- **Enhanced Notifications:** Show ball history size, velocity, and speed information
- **Improved Flexibility:** Brightness range 80-220, color variance <50, broader RGB ranges

### 🐛 Issues Fixed
- Gray/silver ball detection in various lighting conditions
- False positive predictions from detection noise
- Improved ball detection reliability and prediction triggering

---

## v2.4-PREDICTION-OVERLAY
**Release Date:** 2025-08-19  
**APK:** `app-release-v2.4-PREDICTION-OVERLAY.apk`  
**Size:** 49MB  

### 🚀 New Features
- **Ball Trajectory Prediction:** Real-time physics simulation showing where ball will go
- **Color-Coded Path:** Red → Yellow → Green gradient based on time
- **Multi-Color Ball Detection:** Support for various ball types and lighting conditions
- **Overlay System:** Non-intrusive visual overlay that doesn't interfere with gameplay
- **Physics Engine:** Accurate trajectory calculation with gravity and velocity

### 🎯 Core Functionality
- Real-time screen capture and ball detection
- Physics-based trajectory prediction
- Visual overlay rendering
- Accessibility service integration

---

## Version Numbering System

**Format:** `MAJOR.MINOR-FEATURE-DESCRIPTION`

- **MAJOR:** Significant architectural changes or complete rewrites
- **MINOR:** New features, major improvements, or significant bug fixes
- **FEATURE:** Brief description of main feature or fix
- **DESCRIPTION:** Additional context (DEBUG, BETA, STABLE, etc.)

### Version Code Mapping
- v2.4: versionCode 4
- v2.5: versionCode 5  
- v2.6: versionCode 6

---

## Development Notes

### Current Issues (v2.6)
1. **Coordinate System Mismatch:** Ball detection coordinates don't align with overlay rendering
2. **Flat Line Predictions:** Trajectory shows as horizontal line instead of proper curve
3. **Short Prediction Duration:** Predictions disappear too quickly
4. **Detection Accuracy:** Algorithm may be detecting wrong objects instead of actual ball

### Next Version Goals (v2.7)
- [ ] Fix coordinate system alignment between detection and overlay
- [ ] Improve ball detection accuracy to focus on actual ball vs field elements  
- [ ] Adjust validation thresholds to prevent predictions disappearing too quickly
- [ ] Add visual debugging indicators to show detected vs actual ball position
- [ ] Implement coordinate transformation if needed for different screen orientations

### Build Process
Use the automated version update script:
```bash
cd android
./update-version.sh "2.7-COORDINATE-FIX" "Fixed coordinate system mismatch"
```

This will:
1. Update versionCode and versionName in build.gradle
2. Build the release APK
3. Copy to webserver with proper naming
4. Display next steps for web page updates and git commits