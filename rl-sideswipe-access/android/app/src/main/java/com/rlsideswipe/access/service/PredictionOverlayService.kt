package com.rlsideswipe.access.service

import android.app.*
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
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
        
        fun requestTemplateCapture(x: Float, y: Float) {
            instance?.requestTemplateCapture(x, y)
        }
        
        fun enableTouchMode() {
            instance?.overlayView?.enableTouchMode()
        }
        
        fun disableTouchMode() {
            instance?.overlayView?.disableTouchMode()
        }
        
        fun startManualMode() {
            instance?.overlayView?.let { view ->
                view.enableTouchMode()
                // Start manual mode at center of screen
                val centerX = view.width / 2f
                val centerY = view.height / 2f
                view.startManualMode(centerX, centerY)
            }
        }
        
        fun getInstance(): PredictionOverlayService? = instance
        
        fun enableManualPositioning() {
            Log.d(TAG, "🎯 Enabling manual ball positioning mode")
            instance?.overlayView?.enableTouchMode()
        }
    }
    
    private var windowManager: WindowManager? = null
    private var overlayView: PredictionOverlayView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    
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
            // TYPE_ACCESSIBILITY_OVERLAY does not require SYSTEM_ALERT_WINDOW; no overlay settings check needed here
            
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            overlayView = PredictionOverlayView(this)
            
            layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            
            layoutParams!!.gravity = Gravity.TOP or Gravity.START
            
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
    
    fun requestTemplateCapture(x: Float, y: Float) {
        // Request screen capture service to capture template at this position
        val intent = Intent("com.rlsideswipe.access.CAPTURE_TEMPLATE")
        intent.putExtra("x", x)
        intent.putExtra("y", y)
        sendBroadcast(intent)
        Log.d(TAG, "🎯 Requested template capture at ($x, $y)")
    }
    
    fun setOverlayTouchable(touchable: Boolean) {
        try {
            layoutParams?.let { params ->
                if (touchable) {
                    // Make overlay touchable - remove NOT_TOUCHABLE and NOT_FOCUSABLE flags
                    params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                    params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                    Log.d(TAG, "🖱️ Overlay made TOUCHABLE - user can interact")
                } else {
                    // Make overlay non-touchable - add NOT_TOUCHABLE and NOT_FOCUSABLE flags
                    params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    Log.d(TAG, "🖱️ Overlay made NON-TOUCHABLE - touches pass through")
                }
                
                // Update the overlay with new parameters
                overlayView?.let { view ->
                    windowManager?.updateViewLayout(view, params)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating overlay touchability", e)
        }
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
    
    init {
        // Start as non-touchable - touches will pass through to underlying apps
        isClickable = false
        isFocusable = false
        isFocusableInTouchMode = false
        Log.d(TAG, "🖱️ PredictionOverlayView initialized as NON-TOUCHABLE - touches pass through")
    }
    
    private var predictions: List<PredictionOverlayService.PredictionPoint> = emptyList()
    
    // Manual override functionality
    private var manualBallPosition: Pair<Float, Float>? = null
    private var isManualMode = false
    private var isDragging = false
    private val touchRadius = 200f // Touch detection radius around ball (increased for easier grabbing)
    
    // UI Control buttons
    private var showControlButtons = false
    private var acceptButtonRect = RectF()
    private var cancelButtonRect = RectF()
    private var exitButtonRect = RectF()
    
    // Touch mode control
    private var touchModeEnabled = false
    private val buttonSize = 120f
    private val buttonMargin = 20f
    
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
        if (screenWidth <= 0 || screenHeight <= 0) {
            Log.w(TAG, "  ⚠️ Missing screen info, returning original coords")
            return Pair(x, y)
        }
        val scaleX = viewWidth  / screenWidth.toFloat().coerceAtLeast(1f)
        val scaleY = viewHeight / screenHeight.toFloat().coerceAtLeast(1f)
        
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
        
        try {
            // Draw current ball position indicator - either from detection or manual override
            val ballPos: Pair<Float, Float>? =
                if (isManualMode && manualBallPosition != null) {
                    manualBallPosition
                } else if (predictions.isNotEmpty()) {
                    val currentPos = predictions[0]
                    transformCoordinates(currentPos.x, currentPos.y)
                } else {
                    null
                }

            ballPos?.let { (ballX, ballY) ->
                Log.d(TAG, "🎨 Drawing ball at ($ballX, $ballY) manual=$isManualMode")
                
                // Draw VERY VISIBLE ball indicator - different colors for manual vs detected
                if (isManualMode) {
                    paint.color = Color.argb(255, 255, 255, 0) // Bright yellow for manual mode
                    Log.d(TAG, "🎨 Drawing YELLOW manual ball at ($ballX, $ballY)")
                } else {
                    paint.color = Color.argb(255, 255, 0, 255) // Bright magenta for detected
                    Log.d(TAG, "🎨 Drawing MAGENTA detected ball at ($ballX, $ballY)")
                }
                paint.style = Paint.Style.FILL
                paint.strokeWidth = 0f
                
                // Draw large filled square (easier to see than circle)
                val squareSize = 40f
                canvas.drawRect(
                    ballX - squareSize/2, 
                    ballY - squareSize/2,
                    ballX + squareSize/2, 
                    ballY + squareSize/2, 
                    paint
                )
                
                // Draw border around square
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 4f
                paint.color = Color.WHITE
                canvas.drawRect(
                    ballX - squareSize/2, 
                    ballY - squareSize/2,
                    ballX + squareSize/2, 
                    ballY + squareSize/2, 
                    paint
                )
                
                // Draw crosshair at center
                paint.strokeWidth = 3f
                paint.color = Color.WHITE
                canvas.drawLine(ballX - 25f, ballY, ballX + 25f, ballY, paint)
                canvas.drawLine(ballX, ballY - 25f, ballX, ballY + 25f, paint)
                
                // Add text indicator for manual mode
                if (isManualMode) {
                    val modePaint = Paint().apply {
                        color = Color.YELLOW
                        textSize = 30f
                        isAntiAlias = true
                    }
                    canvas.drawText("MANUAL", ballX - 40f, ballY - 50f, modePaint)
                }
                
                Log.d(TAG, "🎯 Ball indicator: (${ballX.toInt()}, ${ballY.toInt()}) ${if (isManualMode) "MANUAL" else "DETECTED"}")
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
            
            // Draw control buttons when in manual mode
            if (showControlButtons) {
                drawControlButtons(canvas)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error drawing prediction overlay", e)
        }
    }
    
    private fun drawControlButtons(canvas: Canvas) {
        // Position buttons at bottom of screen
        val bottomY = height - buttonMargin
        val topY = bottomY - buttonSize
        
        // Accept button (green) - left
        acceptButtonRect.set(
            buttonMargin,
            topY,
            buttonMargin + buttonSize,
            bottomY
        )
        
        Log.d(TAG, "🎨 Button rects - Accept: $acceptButtonRect, screen: ${width}x${height}")
        
        // Cancel button (red) - center
        val centerX = width / 2f
        cancelButtonRect.set(
            centerX - buttonSize / 2f,
            topY,
            centerX + buttonSize / 2f,
            bottomY
        )
        
        // Exit button (gray) - right
        exitButtonRect.set(
            width - buttonMargin - buttonSize,
            topY,
            width - buttonMargin,
            bottomY
        )
        
        // Draw buttons with rounded corners
        val cornerRadius = 20f
        
        // Accept button (green)
        val acceptPaint = Paint().apply {
            color = Color.parseColor("#4CAF50")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(acceptButtonRect, cornerRadius, cornerRadius, acceptPaint)
        
        // Cancel button (red)
        val cancelPaint = Paint().apply {
            color = Color.parseColor("#F44336")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(cancelButtonRect, cornerRadius, cornerRadius, cancelPaint)
        
        // Exit button (gray)
        val exitPaint = Paint().apply {
            color = Color.parseColor("#757575")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(exitButtonRect, cornerRadius, cornerRadius, exitPaint)
        
        // Draw button text
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 32f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        
        val textY = topY + buttonSize / 2f + 12f // Center vertically
        
        canvas.drawText("✓", acceptButtonRect.centerX(), textY, textPaint)
        canvas.drawText("✗", cancelButtonRect.centerX(), textY, textPaint)
        canvas.drawText("EXIT", exitButtonRect.centerX(), textY, textPaint)
        
        // Draw instruction text
        val instructionPaint = Paint().apply {
            color = Color.WHITE
            textSize = 36f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            setShadowLayer(4f, 2f, 2f, Color.BLACK)
        }
        
        val instructionY = topY - 60f
        canvas.drawText("Drag yellow square to ball position", width / 2f, instructionY, instructionPaint)
        canvas.drawText("✓ Accept & Learn  ✗ Cancel  EXIT Manual Mode", width / 2f, instructionY - 50f, instructionPaint)
        
        // Draw touch mode indicator when not in touch mode
        if (!touchModeEnabled) {
            val touchIndicatorPaint = Paint().apply {
                color = Color.argb(200, 0, 255, 0) // Semi-transparent green
                textSize = 32f
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                setShadowLayer(3f, 1f, 1f, Color.BLACK)
            }
            
            // Draw at bottom of screen
            val indicatorY = height - 100f
            canvas.drawText("🖱️ OVERLAY NON-TOUCHABLE - Touches pass through", width / 2f, indicatorY, touchIndicatorPaint)
            canvas.drawText("Use app controls to enable manual ball positioning", width / 2f, indicatorY + 40f, touchIndicatorPaint)
        }
        
        // Draw prominent manual mode activation indicator when not in manual mode and touch mode disabled
        if (!isManualMode && !touchModeEnabled) {
            val manualModeButtonPaint = Paint().apply {
                color = Color.argb(180, 255, 165, 0) // Semi-transparent orange
                textSize = 28f
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                setShadowLayer(4f, 2f, 2f, Color.BLACK)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            
            // Draw prominent button-like indicator at top center
            val buttonY = 120f
            val buttonWidth = 400f
            val buttonHeight = 80f
            val buttonX = width / 2f
            
            // Draw button background
            val buttonBgPaint = Paint().apply {
                color = Color.argb(120, 255, 165, 0) // Semi-transparent orange background
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            val buttonRect = RectF(
                buttonX - buttonWidth/2, 
                buttonY - buttonHeight/2,
                buttonX + buttonWidth/2, 
                buttonY + buttonHeight/2
            )
            canvas.drawRoundRect(buttonRect, 20f, 20f, buttonBgPaint)
            
            // Draw button border
            val buttonBorderPaint = Paint().apply {
                color = Color.argb(255, 255, 165, 0) // Solid orange border
                style = Paint.Style.STROKE
                strokeWidth = 4f
                isAntiAlias = true
            }
            canvas.drawRoundRect(buttonRect, 20f, 20f, buttonBorderPaint)
            
            // Draw button text
            canvas.drawText("🎯 MANUAL BALL MODE AVAILABLE", buttonX, buttonY - 5f, manualModeButtonPaint)
            canvas.drawText("Use React Native app controls to enable", buttonX, buttonY + 25f, manualModeButtonPaint)
        }
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return try {
            // Only handle touch events if touch mode is enabled
            if (!touchModeEnabled) {
                Log.d(TAG, "🖱️ Touch ignored - touch mode disabled, passing through to underlying app")
                return false // Let the touch pass through to underlying apps
            }
            
            val touchX = event.x
            val touchY = event.y
            
            Log.d(TAG, "🖱️ TOUCH EVENT: action=${event.action} at ($touchX, $touchY) manual=$isManualMode dragging=$isDragging touchMode=$touchModeEnabled")
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Check if touching control buttons first
                if (showControlButtons) {
                    Log.d(TAG, "🖱️ Checking buttons - Accept: $acceptButtonRect, Cancel: $cancelButtonRect, Exit: $exitButtonRect")
                    when {
                        acceptButtonRect.contains(touchX, touchY) -> {
                            Log.d(TAG, "✅ Accept button pressed - capturing template and staying in manual mode")
                            captureCurrentTemplate()
                            return true
                        }
                        cancelButtonRect.contains(touchX, touchY) -> {
                            Log.d(TAG, "❌ Cancel button pressed - reverting to auto mode")
                            cancelManualMode()
                            return true
                        }
                        exitButtonRect.contains(touchX, touchY) -> {
                            Log.d(TAG, "🚪 Exit button pressed - completely exiting manual mode")
                            exitManualMode()
                            return true
                        }
                    }
                }
                
                // If not in manual mode, start manual mode on any touch
                if (!isManualMode) {
                    startManualMode(touchX, touchY)
                    // Immediately start dragging since user just placed the ball
                    isDragging = true
                    Log.d(TAG, "🖱️ Started manual mode and dragging from ($touchX, $touchY)")
                    return true
                }
                
                // If in manual mode, check if touching near ball to start dragging
                val ballPos = manualBallPosition ?: Pair(touchX, touchY)
                val distance = kotlin.math.sqrt(
                    (touchX - ballPos.first) * (touchX - ballPos.first) +
                    (touchY - ballPos.second) * (touchY - ballPos.second)
                )
                
                if (distance <= touchRadius) {
                    isDragging = true
                    showControlButtons = false // Hide buttons while dragging
                    Log.d(TAG, "🖱️ Started dragging ball from ($touchX, $touchY), distance=$distance, radius=$touchRadius")
                    invalidate()
                    return true
                } else {
                    Log.d(TAG, "🖱️ Touch too far from ball - distance=$distance, radius=$touchRadius, ball at $ballPos")
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDragging && isManualMode) {
                    manualBallPosition = Pair(touchX, touchY)
                    Log.d(TAG, "🖱️ Ball dragged to ($touchX, $touchY) - manual=$isManualMode, dragging=$isDragging")
                    invalidate()
                    return true
                } else {
                    Log.d(TAG, "🖱️ ACTION_MOVE ignored - manual=$isManualMode, dragging=$isDragging")
                }
            }

            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    isDragging = false
                    Log.d(TAG, "🖱️ Drag ended at (${manualBallPosition?.first}, ${manualBallPosition?.second}) - showing buttons")
                    // Show control buttons after positioning
                    showControlButtons = true
                    invalidate()
                    return true
                } else {
                    Log.d(TAG, "🖱️ ACTION_UP ignored - not dragging, manual=$isManualMode")
                }
            }
            }
            super.onTouchEvent(event)
        } catch (e: Exception) {
            Log.e(TAG, "🚨 ERROR in onTouchEvent: ${e.message}", e)
            false
        }
    }
    
    fun startManualMode(touchX: Float, touchY: Float) {
        isManualMode = true
        manualBallPosition = Pair(touchX, touchY)
        showControlButtons = false // Don't show buttons immediately, wait for drag to finish
        Log.d(TAG, "🖱️ Manual mode started at ($touchX, $touchY) - ready for dragging")
        invalidate()
    }
    
    private fun captureCurrentTemplate() {
        val finalPos = manualBallPosition
        if (finalPos != null && width > 0 && height > 0) {
            // Convert overlay coordinates back to screen coordinates for template capture
            val screenX = finalPos.first * screenWidth / width
            val screenY = finalPos.second * screenHeight / height
            
            Log.d(TAG, "📸 Capturing template at screen coords ($screenX, $screenY)")
            PredictionOverlayService.requestTemplateCapture(screenX, screenY)
            
            // Hide buttons after capture but stay in manual mode
            showControlButtons = false
            invalidate()
        }
    }
    
    private fun cancelManualMode() {
        // Return to auto detection mode but don't exit manual completely
        showControlButtons = false
        Log.d(TAG, "❌ Cancelled manual positioning - returning to auto detection")
        invalidate()
    }
    
    private fun exitManualMode() {
        // Completely exit manual mode
        isManualMode = false
        manualBallPosition = null
        isDragging = false
        showControlButtons = false
        Log.d(TAG, "🚪 Exited manual mode completely")
        invalidate()
        
        // Also disable touch mode to make overlay non-touchable
        disableTouchMode()
    }
    
    // Method to disable manual mode (can be called from service)
    fun disableManualMode() {
        exitManualMode()
    }
    
    // Method to check if in manual mode
    fun isInManualMode(): Boolean = isManualMode
    
    // Get manual ball position for detection service
    fun getManualBallPosition(): Pair<Float, Float>? = manualBallPosition
    
    // Enable touch mode - makes overlay touchable
    fun enableTouchMode() {
        touchModeEnabled = true
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true
        service.setOverlayTouchable(true)
        Log.d(TAG, "🖱️ Touch mode ENABLED - overlay is now touchable")
    }
    
    // Disable touch mode - makes overlay non-touchable
    fun disableTouchMode() {
        touchModeEnabled = false
        isClickable = false
        isFocusable = false
        isFocusableInTouchMode = false
        service.setOverlayTouchable(false)
        Log.d(TAG, "🖱️ Touch mode DISABLED - overlay is now non-touchable")
    }
    
    // Check if touch mode is enabled
    fun isTouchModeEnabled(): Boolean = touchModeEnabled
}