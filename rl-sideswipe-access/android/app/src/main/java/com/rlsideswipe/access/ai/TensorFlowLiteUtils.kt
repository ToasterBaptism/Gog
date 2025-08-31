package com.rlsideswipe.access.ai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Log
import com.rlsideswipe.access.util.OpenCVUtils
import kotlin.math.*

/**
 * Advanced TensorFlow Lite preprocessing utilities
 * Implements computer vision techniques optimized for ball detection
 */
object TensorFlowLiteUtils {
    
    private const val TAG = "TFLiteUtils"
    
    /**
     * TF Lite Best Practice #14: Multi-stage preprocessing pipeline
     */
    fun preprocessForBallDetection(bitmap: Bitmap, targetSize: Int = 320): Bitmap {
        return try {
            Log.d(TAG, "🔄 Starting multi-stage preprocessing pipeline...")
            
            // Stage 1: Adaptive enhancement for ball visibility
            val enhanced = OpenCVUtils.adaptivePreprocessForDetection(bitmap)
            Log.d(TAG, "✅ Stage 1: Adaptive enhancement completed")
            
            // Stage 2: Noise reduction with Gaussian blur
            val denoised = OpenCVUtils.gaussianBlur(enhanced, 1.5f)
            enhanced.recycle()
            Log.d(TAG, "✅ Stage 2: Noise reduction completed")
            
            // Stage 3: Contrast enhancement for better feature detection
            val contrasted = OpenCVUtils.enhanceContrast(denoised, 1.3f)
            denoised.recycle()
            Log.d(TAG, "✅ Stage 3: Contrast enhancement completed")
            
            // Stage 4: Resize to model input size with high-quality filtering
            val resized = Bitmap.createScaledBitmap(contrasted, targetSize, targetSize, true)
            contrasted.recycle()
            Log.d(TAG, "✅ Stage 4: Resizing to ${targetSize}x${targetSize} completed")
            
            // Stage 5: Final normalization
            val normalized = normalizeForInference(resized)
            resized.recycle()
            Log.d(TAG, "✅ Stage 5: Normalization completed")
            
            Log.i(TAG, "🎯 Multi-stage preprocessing pipeline completed successfully")
            normalized
            
        } catch (e: Exception) {
            Log.e(TAG, "🚨 Preprocessing pipeline failed: ${e.message}", e)
            // Fallback to simple scaling
            Bitmap.createScaledBitmap(bitmap, targetSize, targetSize, true)
        }
    }
    
    /**
     * TF Lite Best Practice #15: Specialized normalization for inference
     */
    private fun normalizeForInference(bitmap: Bitmap): Bitmap {
        return try {
            val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(result)
            
            // Apply subtle gamma correction for better model performance
            val paint = Paint().apply {
                colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
                    // Gamma correction matrix (gamma = 0.9)
                    val gamma = 0.9f
                    val invGamma = 1.0f / gamma
                    setSaturation(1.1f) // Slight saturation boost
                    set(floatArrayOf(
                        invGamma, 0f, 0f, 0f, 0f,
                        0f, invGamma, 0f, 0f, 0f,
                        0f, 0f, invGamma, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    ))
                })
            }
            
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
            Log.d(TAG, "✅ Gamma correction and saturation boost applied")
            result
            
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Normalization failed, using original: ${e.message}")
            bitmap
        }
    }
    
    /**
     * TF Lite Best Practice #16: Advanced data augmentation for robustness
     */
    fun augmentForRobustness(bitmap: Bitmap): List<Bitmap> {
        val augmentations = mutableListOf<Bitmap>()
        
        try {
            // Original
            augmentations.add(bitmap.copy(Bitmap.Config.ARGB_8888, false))
            
            // Slight rotation variations (-5° to +5°)
            for (angle in listOf(-3f, 3f)) {
                augmentations.add(rotateBitmap(bitmap, angle))
            }
            
            // Brightness variations
            for (brightness in listOf(0.9f, 1.1f)) {
                augmentations.add(adjustBrightness(bitmap, brightness))
            }
            
            Log.d(TAG, "🔄 Generated ${augmentations.size} augmented versions")
            
        } catch (e: Exception) {
            Log.e(TAG, "🚨 Augmentation failed: ${e.message}", e)
            // Return at least the original
            if (augmentations.isEmpty()) {
                augmentations.add(bitmap.copy(Bitmap.Config.ARGB_8888, false))
            }
        }
        
        return augmentations
    }
    
    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        return try {
            val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Rotation failed: ${e.message}")
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
    }
    
    private fun adjustBrightness(bitmap: Bitmap, factor: Float): Bitmap {
        return try {
            val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(result)
            val paint = Paint().apply {
                colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
                    setScale(factor, factor, factor, 1f)
                })
            }
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
            result
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Brightness adjustment failed: ${e.message}")
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
    }
    
    /**
     * TF Lite Best Practice #17: Post-processing with Non-Maximum Suppression
     */
    fun applyNonMaxSuppression(detections: List<Detection>, iouThreshold: Float = 0.5f): List<Detection> {
        if (detections.size <= 1) return detections
        
        return try {
            val sorted = detections.sortedByDescending { it.conf }
            val result = mutableListOf<Detection>()
            
            for (detection in sorted) {
                var shouldKeep = true
                
                for (kept in result) {
                    val iou = calculateIoU(detection, kept)
                    if (iou > iouThreshold) {
                        shouldKeep = false
                        break
                    }
                }
                
                if (shouldKeep) {
                    result.add(detection)
                }
            }
            
            Log.d(TAG, "🎯 NMS: ${detections.size} -> ${result.size} detections")
            result
            
        } catch (e: Exception) {
            Log.e(TAG, "🚨 NMS failed: ${e.message}", e)
            detections
        }
    }
    
    private fun calculateIoU(det1: Detection, det2: Detection): Float {
        return try {
            val x1 = maxOf(det1.cx - det1.r, det2.cx - det2.r)
            val y1 = maxOf(det1.cy - det1.r, det2.cy - det2.r)
            val x2 = minOf(det1.cx + det1.r, det2.cx + det2.r)
            val y2 = minOf(det1.cy + det1.r, det2.cy + det2.r)
            
            val intersection = maxOf(0f, x2 - x1) * maxOf(0f, y2 - y1)
            val area1 = PI.toFloat() * det1.r * det1.r
            val area2 = PI.toFloat() * det2.r * det2.r
            val union = area1 + area2 - intersection
            
            if (union > 0f) intersection / union else 0f
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ IoU calculation failed: ${e.message}")
            0f
        }
    }
    
    /**
     * TF Lite Best Practice #18: Model performance monitoring
     */
    data class InferenceMetrics(
        val preprocessingTimeMs: Long,
        val inferenceTimeMs: Long,
        val postprocessingTimeMs: Long,
        val totalTimeMs: Long,
        val confidence: Float,
        val detectionFound: Boolean
    )
    
    fun createMetrics(
        preprocessingTime: Long,
        inferenceTime: Long,
        postprocessingTime: Long,
        detection: Detection?
    ): InferenceMetrics {
        return InferenceMetrics(
            preprocessingTimeMs = preprocessingTime / 1_000_000,
            inferenceTimeMs = inferenceTime / 1_000_000,
            postprocessingTimeMs = postprocessingTime / 1_000_000,
            totalTimeMs = (preprocessingTime + inferenceTime + postprocessingTime) / 1_000_000,
            confidence = detection?.conf ?: 0f,
            detectionFound = detection != null
        )
    }
    
    /**
     * TF Lite Best Practice #19: Memory optimization utilities
     */
    fun optimizeMemoryUsage() {
        try {
            System.gc()
            Log.d(TAG, "🧹 Memory optimization requested")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Memory optimization failed: ${e.message}")
        }
    }
    
    /**
     * TF Lite Best Practice #20: Model input validation
     */
    fun validateModelInput(bitmap: Bitmap, expectedSize: Int): Boolean {
        return try {
            val isValid = bitmap.width == expectedSize && 
                         bitmap.height == expectedSize && 
                         !bitmap.isRecycled &&
                         bitmap.config == Bitmap.Config.ARGB_8888
            
            if (!isValid) {
                Log.w(TAG, "⚠️ Invalid model input: ${bitmap.width}x${bitmap.height}, " +
                          "recycled=${bitmap.isRecycled}, config=${bitmap.config}")
            }
            
            isValid
        } catch (e: Exception) {
            Log.e(TAG, "🚨 Input validation failed: ${e.message}", e)
            false
        }
    }
}