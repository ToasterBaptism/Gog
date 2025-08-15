package com.rlsideswipe.access.util

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.media.Image
import android.util.Log
import java.nio.ByteBuffer

object BitmapUtils {
    
    private const val TAG = "BitmapUtils"
    
    fun imageToBitmap(image: Image): Bitmap? {
        return try {
            when (image.format) {
                ImageFormat.YUV_420_888 -> yuv420ToBitmap(image)
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
    
    private fun yuv420ToBitmap(image: Image): Bitmap? {
        val planes = image.planes
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer
        
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        
        val nv21 = ByteArray(ySize + uSize + vSize)
        
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        
        return nv21ToBitmap(nv21, image.width, image.height)
    }
    
    private fun nv21ToBitmap(nv21: ByteArray, width: Int, height: Int): Bitmap? {
        return try {
            val pixels = IntArray(width * height)
            
            // Simple YUV to RGB conversion (simplified for stub)
            for (i in 0 until width * height) {
                val y = (nv21[i].toInt() and 0xFF)
                val gray = y.coerceIn(0, 255)
                pixels[i] = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
            }
            
            Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        } catch (e: Exception) {
            Log.e(TAG, "Error converting NV21 to bitmap", e)
            null
        }
    }
    
    fun resizeBitmap(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }
    
    fun applyHanningWindow(bitmap: Bitmap): Bitmap {
        // Stub implementation - in real version would apply circular Hanning window
        // to reduce edge artifacts for the neural network
        return bitmap
    }
}