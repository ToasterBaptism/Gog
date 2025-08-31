# Task List

1. ✅ Audit project structure and ball detection pipeline end-to-end
Scanned assets, templates, services, overlay, inference engine, preprocessing utils, template manager, and ScreenCapture pipeline
2. ✅ Unify pixel-space and dynamic TF Lite I/O
Implemented dynamic tensor shape handling and quantized I/O support; parse outputs and scale to original frame pixels
3. ✅ Warmup and preprocessing follow dynamic model dims
Warmup now uses inputWidth/inputHeight; preprocessing already uses dynamic target size
4. 🔄 Overlay consolidation (single Accessibility overlay)
MainAccessibilityService overlay used; ensured ScreenCaptureService doesn’t autostart PredictionOverlayService; verify no double overlay
5. ⏳ On-device build, install, and validation
Build release/debug, test on device, verify detection/overlay and logs, check ProGuard keep rules
6. ⏳ Tune template thresholds and logging post TF Lite validation
Once TF Lite detection is verified, raise SIMILARITY_THRESHOLD and remove test points
7. ⏳ ProGuard/keep rules verification for TFLite and overlay
Confirm minifyEnabled release builds work and keep rules sufficient
8. ⏳ Trajectory and notification coherence
Ensure trajectory uses detection output consistently and notifications are updated appropriately

