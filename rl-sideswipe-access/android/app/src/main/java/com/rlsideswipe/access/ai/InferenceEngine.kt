package com.rlsideswipe.access.ai

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

// Default runtime uses the stubbed engine; real ML backends live in flavor-specific sources
class StubInferenceEngine : InferenceEngine {
    companion object { private const val TAG = "StubInferenceEngine" }
    override fun warmup() { Log.d(TAG, "stub warmup") }
    override fun infer(frame: Bitmap): FrameResult = FrameResult(null, System.nanoTime())
    override fun close() { Log.d(TAG, "stub close") }
}
