package com.rlsideswipe.access.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Log
import kotlin.math.*

/**
 * OpenCV-style image processing utilities implemented with Android Graphics API
 * This provides basic computer vision operations without requiring OpenCV dependency
 */
object OpenCVUtils {
    
    private const val TAG = "OpenCVUtils"
    
    /**
     * Apply Gaussian blur to reduce noise
     */
    fun gaussianBlur(bitmap: Bitmap, radius: Float): Bitmap {
        // Simple box blur approximation of Gaussian blur
        return boxBlur(bitmap, radius.toInt())
    }
    
    private fun boxBlur(bitmap: Bitmap, radius: Int): Bitmap {
        if (radius <= 0) return bitmap
        
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        val result = IntArray(width * height)
        
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        // Horizontal pass
        for (y in 0 until height) {
            for (x in 0 until width) {
                var r = 0
                var g = 0
                var b = 0
                var count = 0
                
                for (dx in -radius..radius) {
                    val nx = (x + dx).coerceIn(0, width - 1)
                    val pixel = pixels[y * width + nx]
                    
                    r += (pixel shr 16) and 0xFF
                    g += (pixel shr 8) and 0xFF
                    b += pixel and 0xFF
                    count++
                }
                
                r /= count
                g /= count
                b /= count
                
                result[y * width + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        
        // Vertical pass
        for (x in 0 until width) {
            for (y in 0 until height) {
                var r = 0
                var g = 0
                var b = 0
                var count = 0
                
                for (dy in -radius..radius) {
                    val ny = (y + dy).coerceIn(0, height - 1)
                    val pixel = result[ny * width + x]
                    
                    r += (pixel shr 16) and 0xFF
                    g += (pixel shr 8) and 0xFF
                    b += pixel and 0xFF
                    count++
                }
                
                r /= count
                g /= count
                b /= count
                
                pixels[y * width + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        
        val blurred = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        blurred.setPixels(pixels, 0, width, 0, 0, width, height)
        return blurred
    }
    
    /**
     * Convert to grayscale for better ball detection
     */
    fun toGrayscale(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val grayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val canvas = Canvas(grayscale)
        val paint = Paint()
        
        val colorMatrix = ColorMatrix()
        colorMatrix.setSaturation(0f) // Remove color saturation
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return grayscale
    }
    
    /**
     * Enhance contrast for better ball visibility
     */
    fun enhanceContrast(bitmap: Bitmap, contrast: Float = 1.5f): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val enhanced = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val canvas = Canvas(enhanced)
        val paint = Paint()
        
        val colorMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, 0f,
            0f, contrast, 0f, 0f, 0f,
            0f, 0f, contrast, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return enhanced
    }
    
    /**
     * Apply morphological operations to clean up noise
     */
    fun morphologyClose(bitmap: Bitmap, kernelSize: Int = 3): Bitmap {
        // Simplified morphological closing: dilation followed by erosion
        val dilated = dilate(bitmap, kernelSize)
        return erode(dilated, kernelSize)
    }
    
    private fun dilate(bitmap: Bitmap, kernelSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        val result = IntArray(width * height)
        
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val radius = kernelSize / 2
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                var maxR = 0
                var maxG = 0
                var maxB = 0
                
                for (dy in -radius..radius) {
                    for (dx in -radius..radius) {
                        val nx = (x + dx).coerceIn(0, width - 1)
                        val ny = (y + dy).coerceIn(0, height - 1)
                        val pixel = pixels[ny * width + nx]
                        
                        val r = (pixel shr 16) and 0xFF
                        val g = (pixel shr 8) and 0xFF
                        val b = pixel and 0xFF
                        
                        maxR = max(maxR, r)
                        maxG = max(maxG, g)
                        maxB = max(maxB, b)
                    }
                }
                
                result[y * width + x] = (0xFF shl 24) or (maxR shl 16) or (maxG shl 8) or maxB
            }
        }
        
        val dilated = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        dilated.setPixels(result, 0, width, 0, 0, width, height)
        return dilated
    }
    
    private fun erode(bitmap: Bitmap, kernelSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        val result = IntArray(width * height)
        
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val radius = kernelSize / 2
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                var minR = 255
                var minG = 255
                var minB = 255
                
                for (dy in -radius..radius) {
                    for (dx in -radius..radius) {
                        val nx = (x + dx).coerceIn(0, width - 1)
                        val ny = (y + dy).coerceIn(0, height - 1)
                        val pixel = pixels[ny * width + nx]
                        
                        val r = (pixel shr 16) and 0xFF
                        val g = (pixel shr 8) and 0xFF
                        val b = pixel and 0xFF
                        
                        minR = min(minR, r)
                        minG = min(minG, g)
                        minB = min(minB, b)
                    }
                }
                
                result[y * width + x] = (0xFF shl 24) or (minR shl 16) or (minG shl 8) or minB
            }
        }
        
        val eroded = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        eroded.setPixels(result, 0, width, 0, 0, width, height)
        return eroded
    }
    
    /**
     * Preprocess image for better ball detection
     */
    fun preprocessForDetection(bitmap: Bitmap): Bitmap {
        try {
            // 1. Convert to grayscale
            val grayscale = toGrayscale(bitmap)
            
            // 2. Enhance contrast
            val enhanced = enhanceContrast(grayscale, 1.3f)
            grayscale.recycle()
            
            // 3. Apply slight blur to reduce noise
            val blurred = gaussianBlur(enhanced, 1.5f)
            enhanced.recycle()
            
            return blurred
        } catch (e: Exception) {
            Log.e(TAG, "Error preprocessing image", e)
            return bitmap
        }
    }
    
    /**
     * Calculate image sharpness (Laplacian variance)
     */
    fun calculateSharpness(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        var variance = 0.0
        var mean = 0.0
        var count = 0
        
        // Apply Laplacian kernel
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val center = pixels[y * width + x]
                val centerGray = ((center shr 16) and 0xFF) * 0.299 +
                               ((center shr 8) and 0xFF) * 0.587 +
                               (center and 0xFF) * 0.114
                
                // Laplacian kernel: 0 -1 0; -1 4 -1; 0 -1 0
                val top = pixels[(y - 1) * width + x]
                val bottom = pixels[(y + 1) * width + x]
                val left = pixels[y * width + (x - 1)]
                val right = pixels[y * width + (x + 1)]
                
                val topGray = ((top shr 16) and 0xFF) * 0.299 +
                             ((top shr 8) and 0xFF) * 0.587 +
                             (top and 0xFF) * 0.114
                val bottomGray = ((bottom shr 16) and 0xFF) * 0.299 +
                               ((bottom shr 8) and 0xFF) * 0.587 +
                               (bottom and 0xFF) * 0.114
                val leftGray = ((left shr 16) and 0xFF) * 0.299 +
                              ((left shr 8) and 0xFF) * 0.587 +
                              (left and 0xFF) * 0.114
                val rightGray = ((right shr 16) and 0xFF) * 0.299 +
                               ((right shr 8) and 0xFF) * 0.587 +
                               (right and 0xFF) * 0.114
                
                val laplacian = 4 * centerGray - topGray - bottomGray - leftGray - rightGray
                mean += laplacian
                count++
            }
        }
        
        mean /= count
        
        // Calculate variance
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val center = pixels[y * width + x]
                val centerGray = ((center shr 16) and 0xFF) * 0.299 +
                               ((center shr 8) and 0xFF) * 0.587 +
                               (center and 0xFF) * 0.114
                
                val top = pixels[(y - 1) * width + x]
                val bottom = pixels[(y + 1) * width + x]
                val left = pixels[y * width + (x - 1)]
                val right = pixels[y * width + (x + 1)]
                
                val topGray = ((top shr 16) and 0xFF) * 0.299 +
                             ((top shr 8) and 0xFF) * 0.587 +
                             (top and 0xFF) * 0.114
                val bottomGray = ((bottom shr 16) and 0xFF) * 0.299 +
                               ((bottom shr 8) and 0xFF) * 0.587 +
                               (bottom and 0xFF) * 0.114
                val leftGray = ((left shr 16) and 0xFF) * 0.299 +
                              ((left shr 8) and 0xFF) * 0.587 +
                              (left and 0xFF) * 0.114
                val rightGray = ((right shr 16) and 0xFF) * 0.299 +
                               ((right shr 8) and 0xFF) * 0.587 +
                               (right and 0xFF) * 0.114
                
                val laplacian = 4 * centerGray - topGray - bottomGray - leftGray - rightGray
                variance += (laplacian - mean) * (laplacian - mean)
            }
        }
        
        return (variance / count).toFloat()
    }
}