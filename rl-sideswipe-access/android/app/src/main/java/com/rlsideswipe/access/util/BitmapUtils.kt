package com.rlsideswipe.access.util

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.PixelFormat
import android.media.Image
import android.util.Log
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

object BitmapUtils {
    
    private const val TAG = "BitmapUtils"
    
    fun imageToBitmap(image: Image): Bitmap? {
        return try {
            when (image.format) {
                ImageFormat.YUV_420_888 -> yuv420ToBitmap(image)
                PixelFormat.RGBA_8888 -> rgbaToBitmap(image)
                else -> {
                    Log.w(TAG, "Unsupported image format: ${image.format}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error converting image to bitmap", e)
            null
        }
    }
    
    private fun rgbaToBitmap(image: Image): Bitmap? {
        val planes = image.planes
        if (planes.isEmpty()) return null
        
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width
        
        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        
        bitmap.copyPixelsFromBuffer(buffer)
        
        return if (rowPadding == 0) {
            bitmap
        } else {
            // Crop to remove padding
            val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
            bitmap.recycle()
            croppedBitmap
        }
    }
    
    private fun yuv420ToBitmap(image: Image): Bitmap? {
        val planes = image.planes
        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]
        
        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        
        val yPixelStride = yPlane.pixelStride
        val uvPixelStride = uPlane.pixelStride
        
        val width = image.width
        val height = image.height
        
        return if (yPixelStride == 1 && uvPixelStride == 1) {
            // Packed format - use rowStride-aware conversion
            yuv420PackedToBitmap(
                yBuffer, uBuffer, vBuffer, width, height,
                yPlane.rowStride, uPlane.rowStride, vPlane.rowStride
            )
        } else {
            // Semi-planar or other format - use general conversion
            yuv420GeneralToBitmap(image, yBuffer, uBuffer, vBuffer, yPlane, uPlane, vPlane)
        }
    }
    
    private fun yuv420PackedToBitmap(
        yBuffer: ByteBuffer,
        uBuffer: ByteBuffer,
        vBuffer: ByteBuffer,
        width: Int,
        height: Int,
        yRowStride: Int,
        uRowStride: Int,
        vRowStride: Int
    ): Bitmap? {
        val yData = ByteArray(yBuffer.remaining())
        val uData = ByteArray(uBuffer.remaining())
        val vData = ByteArray(vBuffer.remaining())
        
        yBuffer.get(yData)
        uBuffer.get(uData)
        vBuffer.get(vData)
        
        val pixels = IntArray(width * height)
        
        // Respect per-row stride for all planes
        for (row in 0 until height) {
            val yBase = row * yRowStride
            val uBase = (row / 2) * uRowStride
            val vBase = (row / 2) * vRowStride
            for (col in 0 until width) {
                val yIndex = yBase + col // yPixelStride == 1 in this branch
                val uvCol = col / 2      // 4:2:0 subsampling
                val uIndex = uBase + uvCol
                val vIndex = vBase + uvCol
                
                val yValue = if (yIndex < yData.size) yData[yIndex].toInt() and 0xFF else 0
                val uValue = if (uIndex < uData.size) uData[uIndex].toInt() and 0xFF else 128
                val vValue = if (vIndex < vData.size) vData[vIndex].toInt() and 0xFF else 128
                
                val rgb = yuvToRgb(yValue, uValue, vValue)
                pixels[row * width + col] = rgb
            }
        }
        
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
    
    private fun yuv420GeneralToBitmap(
        image: Image,
        yBuffer: ByteBuffer,
        uBuffer: ByteBuffer,
        vBuffer: ByteBuffer,
        yPlane: Image.Plane,
        uPlane: Image.Plane,
        vPlane: Image.Plane
    ): Bitmap? {
        val width = image.width
        val height = image.height
        val pixels = IntArray(width * height)
        
        val yRowStride = yPlane.rowStride
        val uRowStride = uPlane.rowStride
        val vRowStride = vPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        val uPixelStride = uPlane.pixelStride
        val vPixelStride = vPlane.pixelStride
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val yIndex = y * yRowStride + x * yPixelStride
                val uvRow = y / 2
                val uvCol = x / 2
                val uIndex = uvRow * uRowStride + uvCol * uPixelStride
                val vIndex = uvRow * vRowStride + uvCol * vPixelStride
                
                val yValue = if (yIndex < yBuffer.limit()) yBuffer.get(yIndex).toInt() and 0xFF else 0
                val uValue = if (uIndex < uBuffer.limit()) uBuffer.get(uIndex).toInt() and 0xFF else 128
                val vValue = if (vIndex < vBuffer.limit()) vBuffer.get(vIndex).toInt() and 0xFF else 128
                
                val rgb = yuvToRgb(yValue, uValue, vValue)
                pixels[y * width + x] = rgb
            }
        }
        
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
    
    private fun yuvToRgb(y: Int, u: Int, v: Int): Int {
        // YUV to RGB conversion using ITU-R BT.601 standard
        val yNorm = y - 16
        val uNorm = u - 128
        val vNorm = v - 128
        
        val r = (1.164f * yNorm + 1.596f * vNorm).toInt()
        val g = (1.164f * yNorm - 0.392f * uNorm - 0.813f * vNorm).toInt()
        val b = (1.164f * yNorm + 2.017f * uNorm).toInt()
        
        val red = r.coerceIn(0, 255)
        val green = g.coerceIn(0, 255)
        val blue = b.coerceIn(0, 255)
        
        return (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
    }
    
    fun resizeBitmap(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }
    
    fun downsampleBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }
        
        val scale = minOf(
            maxDimension.toFloat() / width,
            maxDimension.toFloat() / height
        )
        
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    fun cropToSquare(bitmap: Bitmap): Bitmap {
        val size = min(bitmap.width, bitmap.height)
        val x = (bitmap.width - size) / 2
        val y = (bitmap.height - size) / 2
        
        return Bitmap.createBitmap(bitmap, x, y, size, size)
    }
    
    fun applyHanningWindow(bitmap: Bitmap): Bitmap {
        // This is now implemented in the InferenceEngine for better performance
        return bitmap
    }
}