package com.rlsideswipe.access.service

import android.app.Service
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout

class PredictionOverlayService : Service() {
    
    companion object {
        private const val TAG = "PredictionOverlay"
        private var instance: PredictionOverlayService? = null
        
        fun updatePredictions(predictions: List<PredictionPoint>) {
            instance?.updatePrediction(predictions)
        }
        
        fun updateScreenInfo(width: Int, height: Int, isLandscape: Boolean) {
            instance?.updateScreenInfo(width, height, isLandscape)
        }
    }
    
    private var windowManager: WindowManager? = null
    private var overlayView: PredictionOverlayView? = null
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "PredictionOverlayService created")
        createOverlay()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(TAG, "PredictionOverlayService destroyed")
        removeOverlay()
    }
    
    private fun createOverlay() {
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            overlayView = PredictionOverlayView(this)
            
            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            
            layoutParams.gravity = Gravity.TOP or Gravity.START
            
            windowManager?.addView(overlayView, layoutParams)
            Log.d(TAG, "Overlay view added to window manager")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creating overlay", e)
        }
    }
    
    private fun removeOverlay() {
        try {
            overlayView?.let { view ->
                windowManager?.removeView(view)
                overlayView = null
            }
            Log.d(TAG, "Overlay view removed")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing overlay", e)
        }
    }
    
    fun updatePrediction(predictions: List<PredictionPoint>) {
        overlayView?.updatePrediction(predictions)
    }
    
    fun updateScreenInfo(width: Int, height: Int, isLandscape: Boolean) {
        overlayView?.updateScreenInfo(width, height, isLandscape)
    }
    
    data class PredictionPoint(
        val x: Float,
        val y: Float,
        val time: Float
    )
}

class PredictionOverlayView(private val service: PredictionOverlayService) : View(service) {
    
    companion object {
        private const val TAG = "PredictionOverlayView"
    }
    
    private var predictions: List<PredictionOverlayService.PredictionPoint> = emptyList()
    
    // Screen info for coordinate transformations
    private var screenWidth = 0
    private var screenHeight = 0
    private var isLandscapeMode = false
    
    private val paint = Paint().apply {
        isAntiAlias = true
        strokeWidth = 8f
        style = Paint.Style.STROKE
    }
    
    private val ballPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.RED
        alpha = 200
    }
    
    fun updatePrediction(newPredictions: List<PredictionOverlayService.PredictionPoint>) {
        predictions = newPredictions
        post { invalidate() } // Redraw on UI thread
    }
    
    fun updateScreenInfo(width: Int, height: Int, isLandscape: Boolean) {
        screenWidth = width
        screenHeight = height
        isLandscapeMode = isLandscape
        Log.d(TAG, "🖥️ Screen info updated: ${width}x${height}, landscape: $isLandscape")
    }
    
    // Transform detection coordinates to overlay coordinates
    private fun transformCoordinates(x: Float, y: Float): Pair<Float, Float> {
        // Get current view dimensions
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        
        if (screenWidth == 0 || screenHeight == 0) {
            // No screen info yet, use coordinates as-is
            return Pair(x, y)
        }
        
        // Scale coordinates from detection resolution to overlay resolution
        val scaleX = viewWidth / screenWidth.toFloat()
        val scaleY = viewHeight / screenHeight.toFloat()
        
        val transformedX = x * scaleX
        val transformedY = y * scaleY
        
        Log.v(TAG, "🔄 Transform: ($x,$y) -> ($transformedX,$transformedY) [scale: $scaleX,$scaleY]")
        return Pair(transformedX, transformedY)
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (predictions.isEmpty()) return
        
        try {
            // Draw current ball position indicator (large circle for debugging)
            if (predictions.isNotEmpty()) {
                val currentPos = predictions[0]
                val (transformedX, transformedY) = transformCoordinates(currentPos.x, currentPos.y)
                
                paint.color = Color.argb(200, 255, 0, 255) // Bright magenta
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 8f
                canvas.drawCircle(transformedX, transformedY, 30f, paint) // Large circle around detected ball
                
                // Draw crosshair at detected position
                paint.strokeWidth = 4f
                canvas.drawLine(transformedX - 20f, transformedY, transformedX + 20f, transformedY, paint)
                canvas.drawLine(transformedX, transformedY - 20f, transformedX, transformedY + 20f, paint)
                
                Log.d(TAG, "🎯 Ball indicator: original(${"%.1f".format(currentPos.x)},${"%.1f".format(currentPos.y)}) -> transformed(${"%.1f".format(transformedX)},${"%.1f".format(transformedY)})")
            }
            
            // Draw prediction path
            val path = Path()
            var isFirst = true
            
            for (i in predictions.indices) {
                val point = predictions[i]
                val (transformedX, transformedY) = transformCoordinates(point.x, point.y)
                
                // Color gradient from red to yellow to green based on time
                val timeRatio = point.time / 3f // 3 seconds max
                val alpha = (255 * (1f - timeRatio * 0.7f)).toInt().coerceIn(50, 255)
                
                when {
                    timeRatio < 0.33f -> {
                        // Red to Yellow (0-1 second)
                        val ratio = timeRatio / 0.33f
                        paint.color = Color.argb(alpha, 255, (255 * ratio).toInt(), 0)
                    }
                    timeRatio < 0.66f -> {
                        // Yellow to Green (1-2 seconds)
                        val ratio = (timeRatio - 0.33f) / 0.33f
                        paint.color = Color.argb(alpha, (255 * (1f - ratio)).toInt(), 255, 0)
                    }
                    else -> {
                        // Green (2-3 seconds)
                        paint.color = Color.argb(alpha, 0, 255, 0)
                    }
                }
                
                if (isFirst) {
                    path.moveTo(transformedX, transformedY)
                    isFirst = false
                } else {
                    path.lineTo(transformedX, transformedY)
                }
                
                // Draw small circles at prediction points
                if (i % 3 == 0) { // Every 3rd point to avoid clutter
                    canvas.drawCircle(transformedX, transformedY, 6f, paint)
                }
            }
            
            // Draw the path
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 6f
            canvas.drawPath(path, paint)
            
            // Draw current ball position (first point) with special highlight
            if (predictions.isNotEmpty()) {
                val current = predictions.first()
                val (currentX, currentY) = transformCoordinates(current.x, current.y)
                ballPaint.color = Color.RED
                ballPaint.alpha = 255
                canvas.drawCircle(currentX, currentY, 12f, ballPaint)
                
                // Draw velocity arrow
                if (predictions.size > 1) {
                    val next = predictions[1]
                    val (nextX, nextY) = transformCoordinates(next.x, next.y)
                    val arrowPaint = Paint().apply {
                        color = Color.WHITE
                        strokeWidth = 4f
                        style = Paint.Style.STROKE
                    }
                    
                    val dx = nextX - currentX
                    val dy = nextY - currentY
                    val length = kotlin.math.sqrt(dx * dx + dy * dy)
                    
                    if (length > 0) {
                        val scale = 30f / length // Normalize arrow length
                        val endX = currentX + dx * scale
                        val endY = currentY + dy * scale
                        
                        canvas.drawLine(currentX, currentY, endX, endY, arrowPaint)
                        
                        // Draw arrowhead
                        val arrowAngle = kotlin.math.atan2(dy.toDouble(), dx.toDouble())
                        val arrowLength = 10f
                        val arrowAngle1 = arrowAngle + Math.PI * 0.8
                        val arrowAngle2 = arrowAngle - Math.PI * 0.8
                        
                        val arrowX1 = endX + arrowLength * kotlin.math.cos(arrowAngle1).toFloat()
                        val arrowY1 = endY + arrowLength * kotlin.math.sin(arrowAngle1).toFloat()
                        val arrowX2 = endX + arrowLength * kotlin.math.cos(arrowAngle2).toFloat()
                        val arrowY2 = endY + arrowLength * kotlin.math.sin(arrowAngle2).toFloat()
                        
                        canvas.drawLine(endX, endY, arrowX1, arrowY1, arrowPaint)
                        canvas.drawLine(endX, endY, arrowX2, arrowY2, arrowPaint)
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error drawing prediction overlay", e)
        }
    }
}