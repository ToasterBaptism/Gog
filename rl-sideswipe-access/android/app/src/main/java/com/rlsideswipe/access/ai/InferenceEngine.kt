package com.rlsideswipe.access.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log

// Shared data types

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

/**
 * Factory for creating the appropriate inference engine
 */
object InferenceEngineFactory {
    private const val TAG = "InferenceEngineFactory"
    
    fun createEngine(context: Context, preferTensorFlow: Boolean = true): InferenceEngine {
        return if (preferTensorFlow) {
            try {
                Log.d(TAG, "🚀 Attempting to create TensorFlow Lite engine...")
                TFLiteInferenceEngine(context)
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ TensorFlow Lite unavailable, falling back to stub: ${e.message}")
                StubInferenceEngine()
            }
        } else {
            Log.d(TAG, "📝 Creating stub engine as requested")
            StubInferenceEngine()
        }
    }
}

// Fallback stub engine for when TensorFlow Lite is unavailable
class StubInferenceEngine : InferenceEngine {
    companion object { private const val TAG = "StubInferenceEngine" }
    
    override fun warmup() { 
        Log.d(TAG, "📝 Stub warmup - no ML model to warm up")
    }
    
    override fun infer(frame: Bitmap): FrameResult {
        Log.d(TAG, "📝 Stub inference - returning null (will trigger template matching fallback)")
        return FrameResult(null, System.nanoTime())
    }
    
    override fun close() { 
        Log.d(TAG, "📝 Stub close - no resources to clean up")
    }
}
