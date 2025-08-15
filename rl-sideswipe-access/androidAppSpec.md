# RL Sideswipe Access - Android App Specification

## Build Environment

- **Target OS**: Android 11–14
- **CPU ABI**: arm64-v8a (mandatory), armeabi-v7a (optional)
- **Toolchain**: JDK 17, Android Gradle Plugin 8.5.x, Gradle 8.7+
- **React Native**: 0.73.7 (Hermes enabled)
- **Kotlin**: 1.9.24
- **TensorFlow Lite**: 2.14.0 (GPU + NNAPI delegates)
- **OpenCV**: 4.8.0 Android SDK
- **MediaPipe Tasks Vision**: 0.10.14

## Git Information

- **Repository**: ToasterBaptism/Gog
- **Branch**: main
- **Commit Hash**: 22d31d055267539e9ab3bc62e8665c602e685b9d

## Application Structure

### React Native Components
- `src/App.tsx` - Main application component
- `src/screens/StartScreen.tsx` - Primary user interface
- `src/components/PermissionOverlay.tsx` - Permission request overlay
- `src/lib/bridge/NativeControl.ts` - Native bridge interface

### Android Native Components
- `MainActivity.kt` - React Native activity
- `MainApplication.kt` - Application initialization
- `bridge/NativeControlModule.kt` - React Native bridge
- `service/MainAccessibilityService.kt` - Accessibility overlay service
- `service/ScreenCaptureService.kt` - MediaProjection capture service
- `service/OverlayRenderer.kt` - Ball tracking overlay renderer
- `ai/InferenceEngine.kt` - TensorFlow Lite inference
- `ai/TrajectoryPredictor.kt` - Kalman filter trajectory prediction
- `io/AudioHaptics.kt` - Audio cues and haptic feedback
- `util/SelfHealing.kt` - Watchdog and recovery system
- `util/BitmapUtils.kt` - Image processing utilities

## Permissions

- `android.permission.FOREGROUND_SERVICE`
- `android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION`
- `android.permission.VIBRATE`
- Accessibility Service binding permission

## Security Features

- No INTERNET permission declared
- Network security config disables cleartext traffic
- No external domains configured
- Backup disabled for privacy

## Model Information

- **Model File**: `rl_sideswipe_ball_v1.tflite`
- **Input Size**: 320×320 RGB
- **SHA256**: [To be calculated during build]

## Validation Matrix Status

### Functional Tests
- [ ] Overlay renders ball + 1.2s trajectory at ≥15 FPS
- [ ] Audio/haptics trigger under specified conditions
- [ ] Self-healing restarts capture within 3s of stall

### Performance Tests
- [ ] Median per-frame latency <50ms @ 1080p input
- [ ] CPU <40%, RAM <300MB, battery <15%/hr during 15-minute session

### Accessibility Tests
- [ ] No TalkBack conflicts
- [ ] Service description properly set

### Stability Tests
- [ ] 60-minute run without crash or memory growth >10%

### Privacy Tests
- [ ] No network sockets during session

## Build Commands

### Debug Build
```bash
yarn install
cd android && ./gradlew assembleDebug
```

### Release Build
```bash
cd android && ./gradlew assembleRelease
```

## Output Artifacts

- **Debug APK**: `android/app/build/outputs/apk/debug/app-debug.apk` (110MB)
- **Release APK**: `android/app/build/outputs/apk/release/app-release.apk` (61MB)

## Build Validation Results

### Compilation Status
- ✅ **Debug Build**: SUCCESS (2025-08-15 05:11 UTC)
- ✅ **Release Build**: SUCCESS (2025-08-15 05:16 UTC)
- ✅ **Kotlin Compilation**: All stub implementations compile without errors
- ✅ **Resource Processing**: Icons and layouts processed successfully
- ✅ **ProGuard/R8**: Minification successful with custom rules for TensorFlow Lite/MediaPipe

### Build Environment Verification
- ✅ **JDK 17**: OpenJDK 17.0.16
- ✅ **Gradle**: 8.7
- ✅ **Android Gradle Plugin**: 8.5.2
- ✅ **Android SDK**: API 34, Build Tools 34.0.0
- ✅ **Kotlin**: 1.9.24

### Dependencies Status
- ✅ **React Native**: 0.73.7 with Hermes enabled
- ✅ **TensorFlow Lite**: 2.14.0 (GPU + Support libraries)
- ✅ **MediaPipe Tasks Vision**: 0.10.14
- ⚠️ **OpenCV**: Commented out for initial build (requires JAR file)
- ✅ **AndroidX Libraries**: Core, AppCompat, Material, Lifecycle

### Security Compliance
- ✅ **No INTERNET Permission**: Verified in AndroidManifest.xml
- ✅ **Network Security Config**: Cleartext traffic disabled
- ✅ **ProGuard Rules**: Comprehensive obfuscation for release builds

## Implementation Status

✅ Repository scaffold created
✅ React Native app structure
✅ Android project configuration
✅ Kotlin stub implementations
✅ Resource files and layouts
✅ Manifest and permissions
✅ Debug build compilation
✅ Release build compilation
✅ ProGuard/R8 configuration
⏳ MediaProjection capture pipeline (stub)
⏳ TensorFlow Lite inference (stub)
⏳ Trajectory prediction (basic implementation)
⏳ Audio/haptics integration
⏳ Self-healing watchdog
⏳ Performance optimization

## Notes

This specification documents the current state of the RL Sideswipe Access application. The application is designed to provide accessibility assistance for Rocket League Sideswipe by tracking ball movement and predicting trajectory using computer vision and machine learning.

The current implementation includes all required stub components that compile and run, providing the foundation for the complete implementation. The TensorFlow Lite model integration and MediaProjection capture pipeline require completion for full functionality.