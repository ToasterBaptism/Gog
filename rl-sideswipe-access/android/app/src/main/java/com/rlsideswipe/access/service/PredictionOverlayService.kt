package com.rlsideswipe.access.service

import android.app.*
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
import com.rlsideswipe.access.R

class PredictionOverlayService : Service() {
    
    companion object {
        private const val TAG = "PredictionOverlay"
        private const val CHANNEL_ID = "prediction_overlay_channel"
        private const val NOTIFICATION_ID = 2
        private var instance: PredictionOverlayService? = null
        
        fun updatePredictions(predictions: List<PredictionPoint>) {
            Log.d(TAG, "🎯 OVERLAY: Received ${predictions.size} predictions to display")
            predictions.forEachIndexed { index, pred ->
                Log.d(TAG, "🎯 OVERLAY: Point $index: screen(${pred.x}, ${pred.y}) time=${pred.time}")
            }
            
            if (instance == null) {
                Log.w(TAG, "⚠️ OVERLAY: Service instance is null! Overlay may not be running.")
                return
            }
            
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
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        createOverlay()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "PredictionOverlayService onStartCommand")
        return START_STICKY // Restart if killed
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(TAG, "PredictionOverlayService destroyed")
        removeOverlay()
        stopForeground(true) // Remove notification
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Prediction Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows ball prediction overlay"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ball Prediction Overlay")
            .setContentText("Displaying ball trajectory predictions")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
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
                windowManager?.removeViewImmediate(view)
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
        Log.d(TAG, "🎨 OVERLAY VIEW: Updating with ${newPredictions.size} predictions")
        newPredictions.forEachIndexed { index, pred ->
            Log.d(TAG, "🎨 OVERLAY VIEW: Point $index: (${pred.x}, ${pred.y}) time=${pred.time}")
        }
        
        predictions = newPredictions
        post { 
            Log.d(TAG, "🎨 OVERLAY VIEW: invalidate() called - should trigger onDraw()")
            invalidate() 
        } // Redraw on UI thread
    }
    
    fun updateScreenInfo(width: Int, height: Int, isLandscape: Boolean) {
        screenWidth = width
        screenHeight = height
        isLandscapeMode = isLandscape
        Log.d(TAG, "🖥️ Screen info updated: ${width}x${height}, landscape: $isLandscape")
    }
    
    // Transform screen coordinates to overlay coordinates
    private fun transformCoordinates(x: Float, y: Float): Pair<Float, Float> {
        // Get current view dimensions
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        
        Log.d(TAG, "🔍 COORDINATE DEBUG:")
        Log.d(TAG, "  📱 Screen info: ${screenWidth}x${screenHeight}, landscape: $isLandscapeMode")
        Log.d(TAG, "  📺 View dimensions: ${viewWidth}x${viewHeight}")
        Log.d(TAG, "  🎯 Input coordinates (already in screen space): ($x, $y)")
        
        if (viewWidth == 0f || viewHeight == 0f) {
            Log.w(TAG, "  ⚠️ View not ready, using coordinates as-is")
            return Pair(x, y)
        }
        
        // Since coordinates are now already in screen space, we just need to scale to view size
        // Most of the time, view size should match screen size for full-screen overlay
        val scaleX = viewWidth / screenWidth.toFloat()
        val scaleY = viewHeight / screenHeight.toFloat()
        
        val transformedX = x * scaleX
        val transformedY = y * scaleY
        
        Log.d(TAG, "  🔄 Scale factors: X=${"%.3f".format(scaleX)}, Y=${"%.3f".format(scaleY)}")
        Log.d(TAG, "  ✅ Final overlay coords: (${"%.1f".format(x)},${"%.1f".format(y)}) -> (${"%.1f".format(transformedX)},${"%.1f".format(transformedY)})")
        
        return Pair(transformedX, transformedY)
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        Log.d(TAG, "🎨 OVERLAY VIEW: onDraw() called with ${predictions.size} predictions")
        
        // ALWAYS draw a test rectangle to verify overlay is working
        val testPaint = Paint().apply {
            color = Color.MAGENTA
            style = Paint.Style.STROKE
            strokeWidth = 8f
        }
        canvas.drawRect(50f, 50f, 200f, 200f, testPaint)
        Log.d(TAG, "🎨 OVERLAY VIEW: Drew test rectangle at (50,50)-(200,200)")
        
        // Draw screen info for debugging
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 40f
            isAntiAlias = true
        }
        canvas.drawText("Screen: ${screenWidth}x${screenHeight}", 50f, height - 100f, textPaint)
        canvas.drawText("Points: ${predictions.size}", 50f, height - 50f, textPaint)
        
        if (predictions.isEmpty()) {
            Log.d(TAG, "🎨 OVERLAY VIEW: No predictions to draw, but test elements should be visible")
            return
        }
        
        try {
            // Draw current ball position indicator (large circle for debugging)
            if (predictions.isNotEmpty()) {
                val currentPos = predictions[0]
                val (transformedX, transformedY) = transformCoordinates(currentPos.x, currentPos.y)
                
                // Draw VERY VISIBLE ball indicator - bright magenta square (like you mentioned)
                paint.color = Color.argb(255, 255, 0, 255) // Bright magenta - fully opaque
                paint.style = Paint.Style.FILL
                paint.strokeWidth = 0f
                
                // Draw large filled square (easier to see than circle)
                val squareSize = 40f
                canvas.drawRect(
                    transformedX - squareSize/2, 
                    transformedY - squareSize/2,
                    transformedX + squareSize/2, 
                    transformedY + squareSize/2, 
                    paint
                )
                
                // Draw border around square
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 4f
                paint.color = Color.WHITE
                canvas.drawRect(
                    transformedX - squareSize/2, 
                    transformedY - squareSize/2,
                    transformedX + squareSize/2, 
                    transformedY + squareSize/2, 
                    paint
                )
                
                // Draw crosshair at center
                paint.strokeWidth = 3f
                paint.color = Color.WHITE
                canvas.drawLine(transformedX - 25f, transformedY, transformedX + 25f, transformedY, paint)
                canvas.drawLine(transformedX, transformedY - 25f, transformedX, transformedY + 25f, paint)
                
                // Also draw raw coordinates in different color for comparison (smaller)
                paint.color = Color.argb(180, 0, 255, 255) // Cyan - raw coordinates
                paint.style = Paint.Style.FILL
                val rawSquareSize = 20f
                canvas.drawRect(
                    currentPos.x - rawSquareSize/2, 
                    currentPos.y - rawSquareSize/2,
                    currentPos.x + rawSquareSize/2, 
                    currentPos.y + rawSquareSize/2, 
                    paint
                )
                
                Log.d(TAG, "🎯 Ball indicator: screen(${"%.1f".format(currentPos.x)},${"%.1f".format(currentPos.y)}) -> overlay(${"%.1f".format(transformedX)},${"%.1f".format(transformedY)})")
                Log.d(TAG, "🖼️ Canvas dimensions: ${canvas.width}x${canvas.height}")
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