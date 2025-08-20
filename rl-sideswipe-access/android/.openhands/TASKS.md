# Task List

1. ✅ Fix pendingMediaProjectionResult lambda and add timeout in MainActivity.kt
Updated to remove timeout and invoke original callback; added postDelayed to start timeout
2. ⏳ Update version text in rl-sideswipe-access/index.html to match versionName format
Change display to 2.21-OPENCV-ENHANCED; remove leading 'v' unless convention requires
3. ✅ Remove TFLite imports/initialization from ScreenCaptureService and use StubInferenceEngine exclusively
initializeAIComponents now uses StubInferenceEngine; no TF Lite imports; overlay start adjusted
4. ✅ Move overlay start/update from onCreate to startCapture after metrics computed
startCapture ensures overlay starts/updates after computing screenWidth/screenHeight/isLandscapeMode
5. ✅ Make packed YUV conversion stride-safe using rowStride and pixelStride for Y/U/V
Updated to use rowStride for packed branch in yuv420ToBitmap and yuv420PackedToBitmap index calculations
6. 🔄 Isolate TF Lite/MediaPipe via Gradle flavors and move implementations accordingly
Added flavors, moved deps to tfliteImplementation, split interface/stub in main and placeholder tflite class; both flavors build

