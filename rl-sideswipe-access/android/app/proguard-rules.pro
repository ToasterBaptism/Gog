# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# React Native
-keep class com.facebook.react.** { *; }
-keep class com.facebook.hermes.** { *; }
# Keep annotated React methods on app modules to prevent obfuscation/removal
-keepattributes *Annotation*
-keepclassmembers class * extends com.facebook.react.bridge.ReactContextBaseJavaModule {
  @com.facebook.react.bridge.ReactMethod <methods>;
}

# TensorFlow Lite - keep rules to prevent removal/obfuscation of reflective APIs and native bindings
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.gpu.** { *; }
-keep class org.tensorflow.lite.support.** { *; }
-dontwarn org.tensorflow.lite.gpu.**

# MediaPipe
-keep class com.google.mediapipe.** { *; }
-keep class com.google.mediapipe.proto.** { *; }
-dontwarn com.google.mediapipe.proto.**

# OpenCV (removed)
# -keep class org.opencv.** { *; }
-keep class com.rlsideswipe.access.bridge.** { *; }


# Accessibility Service
-keep class com.rlsideswipe.access.service.** { *; }
-keep class com.rlsideswipe.access.ai.** { *; }