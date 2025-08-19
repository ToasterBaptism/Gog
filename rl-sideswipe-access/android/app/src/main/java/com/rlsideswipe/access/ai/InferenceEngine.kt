package com.rlsideswipe.access.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.*
import com.rlsideswipe.access.util.OpenCVUtils

data class Detection(
    val cx: Float,
    val cy: Float,
    val r: Float,
    val conf: Float
)

data class FrameResult(
    val ball: Detection?,
    val timestampNanos: Long
)

interface InferenceEngine {
    fun warmup()
    fun infer(frame: Bitmap): FrameResult
    fun close()
}

// ============================================================================
// TFLiteInferenceEngine class COMPLETELY REMOVED in v2.22 to prevent crashes
// 
// This class was causing crashes even when not directly instantiated.
// The Android runtime was somehow still trying to load TensorFlow Lite code
// despite the import being commented out in ScreenCaptureService.
// 
// All TensorFlow Lite functionality has been permanently removed from the app
// until the compatibility issues can be resolved.
// ============================================================================

/**
 * Stub implementation for when TensorFlow Lite fails to initialize
 * This prevents crashes and allows the service to continue running
 */
class StubInferenceEngine : InferenceEngine {
    
    companion object {
        private const val TAG = "StubInferenceEngine"
    }
    
    init {
        Log.w(TAG, "Using stub inference engine - no AI processing will occur")
    }
    
    override fun warmup() {
        Log.d(TAG, "Stub warmup completed")
    }
    
    override fun infer(frame: Bitmap): FrameResult {
        // Return empty result - no detection
        return FrameResult(null, System.nanoTime())
    }
    
    override fun close() {
        Log.d(TAG, "Stub inference engine closed")
    }
}