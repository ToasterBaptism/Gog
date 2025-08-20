package com.rlsideswipe.access.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log

// This source set is only compiled in the 'tflite' flavor where TF Lite deps are present.
// It provides the TFLiteInferenceEngine implementation while sharing interfaces/types from main.

class TFLiteInferenceEngine(private val context: Context) : InferenceEngine {
    companion object { private const val TAG = "TFLiteInferenceEngine" }
    override fun warmup() { Log.d(TAG, "warmup (tflite flavor)") }
    override fun infer(frame: Bitmap): FrameResult {
        Log.d(TAG, "infer (tflite flavor placeholder)")
        return FrameResult(null, System.nanoTime())
    }
    override fun close() { Log.d(TAG, "close (tflite flavor)") }
}
