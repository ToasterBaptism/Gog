package com.rlsideswipe.access.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.PixelFormat

import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.MutableLiveData
import com.rlsideswipe.access.R
import com.rlsideswipe.access.ai.Detection
import com.rlsideswipe.access.ai.FrameResult
import com.rlsideswipe.access.ai.InferenceEngine
// import com.rlsideswipe.access.ai.TFLiteInferenceEngine // DISABLED to prevent crashes
import com.rlsideswipe.access.ai.StubInferenceEngine
import com.rlsideswipe.access.ai.TrajectoryPredictor
import com.rlsideswipe.access.ai.KalmanTrajectoryPredictor
import com.rlsideswipe.access.util.BitmapUtils
import kotlin.math.sqrt
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.abs

// Helper data class for search area bounds
data class Tuple4(val first: Int, val second: Int, val third: Int, val fourth: Int)

class ScreenCaptureService : Service() {
    
    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "screen_capture_channel"
        private const val TARGET_FPS = 25
        private const val FRAME_INTERVAL_MS = 1000L / TARGET_FPS
        private const val MAX_FRAME_SKIP = 3 // Skip frames if processing is too slow
        private const val PERFORMANCE_LOG_INTERVAL = 5000L // Log performance every 5 seconds
    }
    
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var inferenceEngine: InferenceEngine? = null
    private var trajectoryPredictor: TrajectoryPredictor? = null
    
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private var lastFrameTime = 0L
    private var frameCount = 0
    
    // Screen orientation and dimensions
    private var screenWidth = 0
    private var screenHeight = 0
    private var isLandscapeMode = false
    private var droppedFrames = 0
    private var totalInferenceTime = 0L
    private var lastPerformanceLog = 0L
    private var isProcessingFrame = false
    
    // LiveData for sharing results with overlay
    val frameResults = MutableLiveData<FrameResult?>()
    val trajectoryPoints = MutableLiveData<List<com.rlsideswipe.access.ai.TrajectoryPoint>?>()
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupBackgroundThread()
        lastPerformanceLog = System.currentTimeMillis()
        startPredictionOverlay()
        Log.d(TAG, "ScreenCaptureService created")
    }
    
    private fun setupBackgroundThread() {
        backgroundThread = HandlerThread("ScreenCaptureBackground").apply {
            start()
        }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            Log.d(TAG, "ScreenCaptureService onStartCommand called")
            
            val notification = createNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.d(TAG, "Foreground service started with notification")
            
            val captureIntent = intent?.getParcelableExtra<Intent>("captureIntent")
            if (captureIntent != null) {
                Log.d(TAG, "Starting screen capture with intent")
                // Start capture on background thread to prevent blocking
                backgroundHandler?.post {
                    try {
                        startCapture(captureIntent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start capture on background thread", e)
                        mainHandler.post {
                            stopSelf()
                        }
                    }
                }
            } else {
                Log.e(TAG, "No capture intent provided")
                stopSelf()
                return START_NOT_STICKY
            }
            
            return START_STICKY
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand", e)
            stopSelf()
            return START_NOT_STICKY
        }
    }
    
    inner class LocalBinder : Binder() {
        fun getService(): ScreenCaptureService = this@ScreenCaptureService
    }
    private val binder = LocalBinder()
    override fun onBind(intent: Intent?): IBinder? = binder
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.screen_capture_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.screen_capture_notification_channel_description)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.screen_capture_notification_title))
            .setContentText(getString(R.string.screen_capture_notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()
    }
    
    private fun startCapture(captureIntent: Intent) {
        try {
            Log.d(TAG, "Initializing MediaProjection...")
            val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mediaProjectionManager.getMediaProjection(Activity.RESULT_OK, captureIntent)
            
            if (mediaProjection == null) {
                Log.e(TAG, "Failed to create MediaProjection")
                stopSelf()
                return
            }
            
            Log.d(TAG, "Getting display metrics...")
            val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            val displayMetrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            
            val width = displayMetrics.widthPixels
            val height = displayMetrics.heightPixels
            val density = displayMetrics.densityDpi
            val rotation = windowManager.defaultDisplay.rotation
            
            // Store screen info for coordinate transformations
            screenWidth = width
            screenHeight = height
            isLandscapeMode = width > height
            
            Log.d(TAG, "🖥️ Display: ${width}x${height}, density: $density, rotation: $rotation")
            Log.d(TAG, "📱 Orientation: ${if (isLandscapeMode) "LANDSCAPE" else "PORTRAIT"}")
            
            // Use RGBA_8888 for stability (avoid complex YUV conversion crashes)
            val format = PixelFormat.RGBA_8888
            
            Log.d(TAG, "Creating ImageReader with format: $format")
            imageReader = ImageReader.newInstance(width, height, format, 2) // Reduced buffer size for stability
            imageReader?.setOnImageAvailableListener(imageAvailableListener, backgroundHandler)
            
            Log.d(TAG, "Creating VirtualDisplay...")
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null, null
            )
            
            if (virtualDisplay == null) {
                Log.e(TAG, "Failed to create VirtualDisplay")
                stopSelf()
                return
            }
            
            Log.d(TAG, "Screen capture started successfully: ${width}x${height}")
            
            // Initialize AI components safely
            initializeAIComponents()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting screen capture", e)
            stopSelf()
        }
    }
    
    private fun initializeAIComponents() {
        backgroundHandler?.post {
            try {
                Log.d(TAG, "Initializing AI components...")
                Log.i(TAG, "=== v2.22 TENSORFLOW LITE CLASS COMPLETELY REMOVED ===")
                Log.i(TAG, "=== NO TENSORFLOW LITE CODE EXISTS IN THE APP ANYMORE ===")
                
                // Initialize inference engine - using stub only to prevent TensorFlow Lite crashes
                inferenceEngine = try {
                    Log.d(TAG, "Initializing inference engine...")
                    Log.i(TAG, "=== USING STUB INFERENCE ENGINE ONLY - NO TENSORFLOW LITE ===")
                    StubInferenceEngine()
                } catch (e: Exception) {
                    Log.w(TAG, "Inference engine initialization failed, using stub: ${e.message}", e)
                    StubInferenceEngine()
                }
                
                // Initialize trajectory predictor
                trajectoryPredictor = try {
                    val predictor = KalmanTrajectoryPredictor()
                    Log.d(TAG, "Kalman trajectory predictor created successfully")
                    predictor
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create trajectory predictor: ${e.message}", e)
                    null
                }
                
                // Warmup inference engine
                try {
                    inferenceEngine?.warmup()
                    Log.i(TAG, "AI components initialized and warmed up successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to warmup inference engine: ${e.message}", e)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize AI components", e)
                // Continue without AI - service will still capture but won't process
            }
        }
    }
    
    private val imageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        try {
            val currentTime = System.currentTimeMillis()
            
            // Skip frame if we're still processing the previous one
            if (isProcessingFrame) {
                try {
                    reader.acquireLatestImage()?.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing skipped image", e)
                }
                droppedFrames++
                return@OnImageAvailableListener
            }
            
            // Throttle frame processing to target FPS
            if (currentTime - lastFrameTime < FRAME_INTERVAL_MS) {
                try {
                    reader.acquireLatestImage()?.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing throttled image", e)
                }
                return@OnImageAvailableListener
            }
            
            lastFrameTime = currentTime
            frameCount++
            
            val image = try {
                reader.acquireLatestImage()
            } catch (e: Exception) {
                Log.e(TAG, "Error acquiring image", e)
                null
            }
            
            if (image != null) {
                try {
                    isProcessingFrame = true
                    processFrame(image)
                } finally {
                    try {
                        image.close()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error closing image", e)
                    }
                }
            }
            
            // Log performance metrics periodically
            if (currentTime - lastPerformanceLog > PERFORMANCE_LOG_INTERVAL) {
                logPerformanceMetrics(currentTime)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Critical error in imageAvailableListener", e)
            isProcessingFrame = false
        }
    }
    
    private fun processFrame(image: Image) {
        val startTime = System.nanoTime()
        
        try {
            // Simple and safe frame processing
            val bitmap = convertImageToBitmapSafely(image)
            if (bitmap != null) {
                // Simple ball detection instead of complex AI
                val ballDetection = detectBallSimple(bitmap)
                
                if (ballDetection != null) {
                    // Post results on main thread
                    mainHandler.post {
                        frameResults.value = ballDetection
                        
                        val trajectory = trajectoryPredictor?.update(ballDetection.ball, System.currentTimeMillis())
                        if (trajectory != null) {
                            trajectoryPoints.value = trajectory
                        }
                        
                        // Update notification to show ball detection is working
                        updateNotificationWithBallDetection(ballDetection.ball)
                    }
                }
                bitmap.recycle()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame", e)
        } finally {
            val processingTime = (System.nanoTime() - startTime) / 1_000_000 // Convert to ms
            totalInferenceTime += processingTime
            isProcessingFrame = false
            
            if (processingTime > 40) { // Log slow frames
                Log.w(TAG, "Slow frame processing: ${processingTime}ms")
            }
        }
    }
    
    private fun convertImageToBitmapSafely(image: Image): Bitmap? {
        return try {
            // Only handle RGBA format to avoid complex YUV conversion crashes
            if (image.format == PixelFormat.RGBA_8888) {
                val planes = image.planes
                if (planes.isNotEmpty()) {
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
                    
                    if (rowPadding == 0) {
                        bitmap
                    } else {
                        // Crop to remove padding
                        val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
                        bitmap.recycle()
                        croppedBitmap
                    }
                } else null
            } else {
                // For other formats, create a simple placeholder
                Log.d(TAG, "Unsupported image format: ${image.format}, creating placeholder")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Safe bitmap conversion failed", e)
            null
        }
    }
    
    private fun detectBallSimple(bitmap: Bitmap): FrameResult? {
        return try {
            // Shape-based ball detection using circular object detection
            val width = bitmap.width
            val height = bitmap.height
            
            // Adjust search area based on orientation
            val (searchStartX, searchEndX, searchStartY, searchEndY) = if (isLandscapeMode) {
                // In landscape: avoid UI elements on left/right sides, focus on center area
                val searchStartX = (width * 0.1).toInt() // Skip left 10% (UI elements)
                val searchEndX = (width * 0.9).toInt() // Skip right 10% (UI elements)
                val searchStartY = (height * 0.1).toInt() // Skip top 10%
                val searchEndY = (height * 0.9).toInt() // Skip bottom 10%
                Tuple4(searchStartX, searchEndX, searchStartY, searchEndY)
            } else {
                // In portrait: focus on upper area (original logic)
                val searchStartX = 0
                val searchEndX = width
                val searchStartY = (height * 0.1).toInt() // Skip top 10% (UI elements)
                val searchEndY = (height * 0.75).toInt() // Only search upper 75% of screen
                Tuple4(searchStartX, searchEndX, searchStartY, searchEndY)
            }
            
            Log.d(TAG, "🔍 Search area: X($searchStartX-$searchEndX), Y($searchStartY-$searchEndY) [${if (isLandscapeMode) "LANDSCAPE" else "PORTRAIT"}]")
            
            // Detect circular objects (balls) using shape analysis
            val detectedCircles = detectCircularObjects(bitmap, searchStartX, searchEndX, searchStartY, searchEndY)
            
            if (detectedCircles.isNotEmpty()) {
                // Find the best ball candidate based on size, position, and circularity
                val bestBall = findBestBallCandidate(detectedCircles, width, height)
                
                if (bestBall != null) {
                    val bestX = bestBall.x
                    val bestY = bestBall.y
                    val confidence = bestBall.confidence
                    Log.d(TAG, "🎯 BALL DETECTED: Position ($bestX, $bestY), Confidence: ${"%.2f".format(confidence)}")
                    Log.d(TAG, "📱 Screen info: ${screenWidth}x${screenHeight}, landscape: $isLandscapeMode")
                    Log.d(TAG, "🖼️ Image dimensions: ${width}x${height}")
                    Log.d(TAG, "📊 Ball position relative to image: X=${"%.1f".format(bestX/width*100)}%, Y=${"%.1f".format(bestY/height*100)}%")
                    Log.d(TAG, "🔍 Search area used: ($searchStartX, $searchStartY) to ($searchEndX, $searchEndY)")
                    
                    // Add to ball tracking history
                    val currentTime = System.currentTimeMillis()
                    addBallToHistory(bestX, bestY, currentTime)
                    
                    // Calculate velocity and predict trajectory
                    if (ballHistory.size >= 2) {
                        val velocity = calculateBallVelocity()
                        if (velocity != null) {
                            val speed = sqrt(velocity.first * velocity.first + velocity.second * velocity.second)
                            Log.d(TAG, "Ball velocity: (${velocity.first}, ${velocity.second}), speed: $speed px/s")
                            
                            // Show predictions if ball is moving fast enough
                            if (speed > 30) { // Reasonable speed threshold
                                val predictions = predictBallTrajectory(bestX, bestY, velocity.first, velocity.second)
                                Log.d(TAG, "Generated ${predictions.size} prediction points")
                                
                                // Create Detection object for the ball
                                val ballDetection = Detection(
                                    cx = bestX / width,
                                    cy = bestY / height,
                                    r = bestBall.radius / width.coerceAtLeast(height),
                                    conf = confidence
                                )
                                
                                updatePredictionOverlay(predictions)
                                updateNotificationWithBallDetection(ballDetection)
                                
                                return FrameResult(ballDetection, System.nanoTime())
                            } else {
                                Log.d(TAG, "Ball speed too low for prediction: $speed px/s")
                            }
                        }
                    }
                    
                    // Return current position even without prediction for debugging
                    val ballDetection = Detection(
                        cx = bestX / width,
                        cy = bestY / height,
                        r = bestBall.radius / width.coerceAtLeast(height),
                        conf = confidence
                    )
                    
                    updatePredictionOverlay(listOf(PredictionPoint(bestX, bestY, 0f)))
                    updateNotificationWithBallDetection(ballDetection)
                    
                    return FrameResult(ballDetection, System.nanoTime())
                }
            }
            
            null
        } catch (e: Exception) {
            Log.e(TAG, "Simple ball detection failed", e)
            null
        }
    }
    
    // Data class for detected circular objects
    data class CircleCandidate(
        val x: Float,
        val y: Float,
        val radius: Float,
        val confidence: Float,
        val edgeStrength: Float
    )
    
    // Detect circular objects using edge detection and shape analysis
    private fun detectCircularObjects(bitmap: Bitmap, startX: Int, endX: Int, startY: Int, endY: Int): List<CircleCandidate> {
        val circles = mutableListOf<CircleCandidate>()
        
        try {
            // Expected ball size range (in pixels)
            val minRadius = 15
            val maxRadius = 80
            val searchStep = 8 // Check every 8 pixels for performance
            
            Log.d(TAG, "🔍 Searching for circles in area ($startX,$startY) to ($endX,$endY)")
            
            // Grid search for circular patterns
            for (centerY in startY until endY step searchStep) {
                for (centerX in startX until endX step searchStep) {
                    // Test different radii
                    for (testRadius in minRadius..maxRadius step 5) {
                        val circleScore = analyzeCircularPattern(bitmap, centerX, centerY, testRadius)
                        
                        if (circleScore > 0.3f) { // Minimum circularity threshold
                            val edgeStrength = calculateEdgeStrength(bitmap, centerX, centerY, testRadius)
                            val confidence = (circleScore * 0.7f + edgeStrength * 0.3f).coerceAtMost(1f)
                            
                            if (confidence > 0.4f) { // Minimum confidence threshold
                                circles.add(CircleCandidate(
                                    x = centerX.toFloat(),
                                    y = centerY.toFloat(),
                                    radius = testRadius.toFloat(),
                                    confidence = confidence,
                                    edgeStrength = edgeStrength
                                ))
                                
                                Log.d(TAG, "🎯 Circle candidate: ($centerX,$centerY) r=$testRadius, confidence=${"%.2f".format(confidence)}")
                            }
                        }
                    }
                }
            }
            
            Log.d(TAG, "🔍 Found ${circles.size} circle candidates")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in circle detection", e)
        }
        
        return circles
    }
    
    // Analyze circular pattern by checking pixel intensity changes around a circle
    private fun analyzeCircularPattern(bitmap: Bitmap, centerX: Int, centerY: Int, radius: Int): Float {
        return try {
            val numSamples = 16 // Sample points around the circle
            var edgeCount = 0
            var totalSamples = 0
            
            for (i in 0 until numSamples) {
                val angle = (i * 2 * Math.PI / numSamples)
                val x1 = (centerX + (radius - 3) * cos(angle)).toInt()
                val y1 = (centerY + (radius - 3) * sin(angle)).toInt()
                val x2 = (centerX + (radius + 3) * cos(angle)).toInt()
                val y2 = (centerY + (radius + 3) * sin(angle)).toInt()
                
                if (isValidPixel(bitmap, x1, y1) && isValidPixel(bitmap, x2, y2)) {
                    val innerBrightness = getPixelBrightness(bitmap.getPixel(x1, y1))
                    val outerBrightness = getPixelBrightness(bitmap.getPixel(x2, y2))
                    
                    // Look for brightness difference (edge)
                    if (abs(innerBrightness - outerBrightness) > 30) {
                        edgeCount++
                    }
                    totalSamples++
                }
            }
            
            if (totalSamples > 0) edgeCount.toFloat() / totalSamples else 0f
            
        } catch (e: Exception) {
            0f
        }
    }
    
    // Calculate edge strength around a circular area
    private fun calculateEdgeStrength(bitmap: Bitmap, centerX: Int, centerY: Int, radius: Int): Float {
        return try {
            var totalEdgeStrength = 0f
            var sampleCount = 0
            
            // Sample points around the circle perimeter
            for (angle in 0 until 360 step 15) {
                val radians = Math.toRadians(angle.toDouble())
                val x = (centerX + radius * cos(radians)).toInt()
                val y = (centerY + radius * sin(radians)).toInt()
                
                if (isValidPixel(bitmap, x, y)) {
                    val edgeStrength = calculateLocalEdgeStrength(bitmap, x, y)
                    totalEdgeStrength += edgeStrength
                    sampleCount++
                }
            }
            
            if (sampleCount > 0) totalEdgeStrength / sampleCount else 0f
            
        } catch (e: Exception) {
            0f
        }
    }
    
    // Calculate local edge strength using gradient
    private fun calculateLocalEdgeStrength(bitmap: Bitmap, x: Int, y: Int): Float {
        return try {
            if (!isValidPixel(bitmap, x-1, y) || !isValidPixel(bitmap, x+1, y) ||
                !isValidPixel(bitmap, x, y-1) || !isValidPixel(bitmap, x, y+1)) {
                return 0f
            }
            
            val centerBrightness = getPixelBrightness(bitmap.getPixel(x, y))
            val leftBrightness = getPixelBrightness(bitmap.getPixel(x-1, y))
            val rightBrightness = getPixelBrightness(bitmap.getPixel(x+1, y))
            val topBrightness = getPixelBrightness(bitmap.getPixel(x, y-1))
            val bottomBrightness = getPixelBrightness(bitmap.getPixel(x, y+1))
            
            val gradientX = abs(rightBrightness - leftBrightness)
            val gradientY = abs(bottomBrightness - topBrightness)
            
            sqrt(gradientX * gradientX + gradientY * gradientY) / 255f
            
        } catch (e: Exception) {
            0f
        }
    }
    
    // Find the best ball candidate from detected circles
    private fun findBestBallCandidate(circles: List<CircleCandidate>, imageWidth: Int, imageHeight: Int): CircleCandidate? {
        if (circles.isEmpty()) return null
        
        // Score circles based on multiple factors
        val scoredCircles = circles.map { circle ->
            var score = circle.confidence
            
            // Prefer circles in the center area of the search region
            val centerX = imageWidth / 2f
            val centerY = imageHeight / 2f
            val distanceFromCenter = sqrt((circle.x - centerX) * (circle.x - centerX) + (circle.y - centerY) * (circle.y - centerY))
            val maxDistance = sqrt(centerX * centerX + centerY * centerY)
            val centerBonus = 1f - (distanceFromCenter / maxDistance) * 0.3f // Up to 30% bonus for center position
            score *= centerBonus
            
            // Prefer medium-sized circles (typical ball size)
            val idealRadius = 35f
            val radiusDiff = abs(circle.radius - idealRadius)
            val radiusBonus = 1f - (radiusDiff / idealRadius) * 0.2f // Up to 20% penalty for size difference
            score *= radiusBonus.coerceAtLeast(0.5f)
            
            // Boost score for high edge strength
            score *= (1f + circle.edgeStrength * 0.5f)
            
            Pair(circle, score)
        }
        
        // Return the highest scoring circle
        val bestCircle = scoredCircles.maxByOrNull { it.second }
        Log.d(TAG, "🎯 Best circle: (${bestCircle?.first?.x}, ${bestCircle?.first?.y}) score=${"%.2f".format(bestCircle?.second ?: 0f)}")
        
        return bestCircle?.first
    }
    
    // Helper function to check if pixel coordinates are valid
    private fun isValidPixel(bitmap: Bitmap, x: Int, y: Int): Boolean {
        return x >= 0 && x < bitmap.width && y >= 0 && y < bitmap.height
    }
    
    // Helper function to get pixel brightness
    private fun getPixelBrightness(pixel: Int): Float {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (r * 0.299f + g * 0.587f + b * 0.114f) // Standard luminance formula
    }

    private fun countBallPixelsInRegion(bitmap: Bitmap, startX: Int, startY: Int, size: Int): Int {
        return try {
            var count = 0
            val endX = (startX + size).coerceAtMost(bitmap.width)
            val endY = (startY + size).coerceAtMost(bitmap.height)
            
            for (y in startY until endY step 5) { // Sample every 5 pixels for performance
                for (x in startX until endX step 5) {
                    val pixel = bitmap.getPixel(x, y)
                    if (isBallColor(pixel)) {
                        count++
                    }
                }
            }
            count
        } catch (e: Exception) {
            0
        }
    }
    
    private fun isBallColor(pixel: Int): Boolean {
        val red = (pixel shr 16) and 0xFF
        val green = (pixel shr 8) and 0xFF
        val blue = pixel and 0xFF
        
        // Method 1: Orange/Red balls (classic Rocket League)
        val isOrangeRed = red > 150 && green > 50 && green < 200 && blue < 100
        
        // Method 2: Gray/Silver balls (enhanced for complex lighting, more specific)
        val brightness = (red + green + blue) / 3
        val colorVariance = Math.max(Math.max(Math.abs(red - green), Math.abs(green - blue)), Math.abs(red - blue))
        // More specific detection to avoid field elements
        val isGraySilver = brightness in 100..180 && 
                          colorVariance < 35 && // Tighter variance for more specificity
                          red in 90..170 && green in 90..170 && blue in 90..170 && // Tighter range
                          // Additional check: avoid very dark or very bright pixels
                          brightness > 95 && brightness < 185
        
        // Method 3: White/Bright balls (enhanced)
        val isWhiteBright = brightness > 180 && 
                           colorVariance < 60 && // More flexible for bright surfaces
                           (red > 150 || green > 150 || blue > 150) // At least one channel bright
        
        // Method 4: Yellow/Golden balls
        val isYellowGold = red > 180 && green > 150 && blue < 120 && red > green && green > blue
        
        // Method 5: Dark metallic balls (for shadowed/darker areas)
        val isDarkMetallic = brightness in 50..120 && colorVariance < 30 &&
                            red in 40..130 && green in 40..130 && blue in 40..130

        return isOrangeRed || isGraySilver || isWhiteBright || isYellowGold || isDarkMetallic
    }
    
    private var lastBallDetectionTime = 0L
    private var ballDetectionCount = 0
    
    // Ball tracking for prediction
    private data class BallPosition(
        val x: Float,
        val y: Float,
        val timestamp: Long
    )
    
    private val ballHistory = mutableListOf<BallPosition>()
    private val maxHistorySize = 10 // Keep last 10 positions for velocity calculation
    
    // Prediction overlay
    private var predictionOverlayService: Intent? = null
    
    private fun addBallToHistory(x: Float, y: Float, timestamp: Long) {
        ballHistory.add(BallPosition(x, y, timestamp))
        
        // Keep only recent positions
        while (ballHistory.size > maxHistorySize) {
            ballHistory.removeAt(0)
        }
        
        // Remove old positions (older than 2 seconds)
        val cutoffTime = timestamp - 2000
        ballHistory.removeAll { it.timestamp < cutoffTime }
    }
    
    private fun calculateBallVelocity(): Pair<Float, Float>? {
        if (ballHistory.size < 2) return null
        
        // Use linear regression to get better velocity estimate
        val recent = ballHistory.takeLast(5) // Use last 5 positions
        if (recent.size < 2) return null
        
        val first = recent.first()
        val last = recent.last()
        val timeDiff = (last.timestamp - first.timestamp) / 1000f // Convert to seconds
        
        if (timeDiff <= 0) return null
        
        val velocityX = (last.x - first.x) / timeDiff // pixels per second
        val velocityY = (last.y - first.y) / timeDiff // pixels per second
        
        return Pair(velocityX, velocityY)
    }
    
    private data class PredictionPoint(
        val x: Float,
        val y: Float,
        val time: Float
    )
    
    private fun predictBallTrajectory(startX: Float, startY: Float, velocityX: Float, velocityY: Float): List<PredictionPoint> {
        val predictions = mutableListOf<PredictionPoint>()
        val screenWidth = resources.displayMetrics.widthPixels.toFloat()
        val screenHeight = resources.displayMetrics.heightPixels.toFloat()
        
        // Physics constants for Rocket League Sideswipe
        val gravity = 800f // pixels per second squared (adjusted for mobile screen)
        val bounceDamping = 0.7f // Energy loss on bounce
        val timeStep = 0.05f // 50ms steps
        val maxPredictionTime = 3f // Predict up to 3 seconds ahead
        
        var currentX = startX
        var currentY = startY
        var currentVelX = velocityX
        var currentVelY = velocityY
        var time = 0f
        
        // Game field boundaries (approximate for Rocket League Sideswipe)
        val fieldLeft = screenWidth * 0.1f
        val fieldRight = screenWidth * 0.9f
        val fieldTop = screenHeight * 0.2f
        val fieldBottom = screenHeight * 0.8f
        
        while (time < maxPredictionTime && predictions.size < 60) { // Max 60 points
            time += timeStep
            
            // Apply gravity
            currentVelY += gravity * timeStep
            
            // Update position
            currentX += currentVelX * timeStep
            currentY += currentVelY * timeStep
            
            // Bounce off walls
            if (currentX <= fieldLeft || currentX >= fieldRight) {
                currentVelX = -currentVelX * bounceDamping
                currentX = if (currentX <= fieldLeft) fieldLeft else fieldRight
            }
            
            // Bounce off floor/ceiling
            if (currentY <= fieldTop || currentY >= fieldBottom) {
                currentVelY = -currentVelY * bounceDamping
                currentY = if (currentY <= fieldTop) fieldTop else fieldBottom
            }
            
            predictions.add(PredictionPoint(currentX, currentY, time))
            
            // Stop if ball is moving very slowly
            val speed = kotlin.math.sqrt(currentVelX * currentVelX + currentVelY * currentVelY)
            if (speed < 50f) break // Less than 50 pixels per second
        }
        
        return predictions
    }
    
    private fun startPredictionOverlay() {
        try {
            predictionOverlayService = Intent(this, PredictionOverlayService::class.java)
            startService(predictionOverlayService)
            
            // Pass screen info to overlay service for coordinate transformation
            Log.d(TAG, "📡 Passing screen info to overlay: ${screenWidth}x${screenHeight}, landscape: $isLandscapeMode")
            PredictionOverlayService.updateScreenInfo(screenWidth, screenHeight, isLandscapeMode)
            
            Log.d(TAG, "Prediction overlay service started with screen info: ${screenWidth}x${screenHeight}, landscape: $isLandscapeMode")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting prediction overlay", e)
        }
    }
    
    private fun stopPredictionOverlay() {
        try {
            predictionOverlayService?.let { intent ->
                stopService(intent)
                predictionOverlayService = null
                Log.d(TAG, "Prediction overlay service stopped")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping prediction overlay", e)
        }
    }
    
    private fun updatePredictionOverlay(predictions: List<PredictionPoint>) {
        try {
            // Convert to overlay service format
            val overlayPredictions = predictions.map { 
                PredictionOverlayService.PredictionPoint(it.x, it.y, it.time) 
            }
            
            // Send update directly to overlay service
            PredictionOverlayService.updatePredictions(overlayPredictions)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating prediction overlay", e)
        }
    }
    
    private fun updateNotificationWithBallDetection(ball: Detection?) {
        ball?.let {
            val currentTime = System.currentTimeMillis()
            ballDetectionCount++
            
            // Update notification every 2 seconds to show ball detection is working
            if (currentTime - lastBallDetectionTime > 2000) {
                lastBallDetectionTime = currentTime
                
                val velocityInfo = calculateBallVelocity()?.let { (vx, vy) ->
                    val speed = kotlin.math.sqrt(vx * vx + vy * vy)
                    " | Speed: ${speed.toInt()}px/s"
                } ?: ""
                
                val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("RL Sideswipe Access - BALL DETECTED!")
                    .setContentText("🎯 Ball at (${(it.cx * 100).toInt()}%, ${(it.cy * 100).toInt()}%) | Conf: ${(it.conf * 100).toInt()}% | History: ${ballHistory.size}$velocityInfo")
                    .setSmallIcon(R.drawable.ic_notification)
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build()
                
                val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, notification)
                
                Log.i(TAG, "🎯 BALL DETECTION WORKING! Position: (${(it.cx * 100).toInt()}%, ${(it.cy * 100).toInt()}%), Confidence: ${(it.conf * 100).toInt()}%, Total detections: $ballDetectionCount")
            }
        }
    }
    
    private fun logPerformanceMetrics(currentTime: Long) {
        val avgInferenceTime = if (frameCount > 0) totalInferenceTime / frameCount else 0
        val actualFps = frameCount * 1000f / (currentTime - lastPerformanceLog)
        val dropRate = if (frameCount > 0) droppedFrames * 100f / (frameCount + droppedFrames) else 0f
        
        Log.i(TAG, "Performance: ${actualFps.toInt()}fps, avg inference: ${avgInferenceTime}ms, drop rate: ${dropRate.toInt()}%")
        
        // Reset counters
        frameCount = 0
        droppedFrames = 0
        totalInferenceTime = 0
        lastPerformanceLog = currentTime
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopCapture()
        stopPredictionOverlay()
        
        backgroundHandler?.post {
            inferenceEngine?.close()
        }
        
        backgroundThread?.quitSafely()
        backgroundThread = null
        backgroundHandler = null
        
        Log.d(TAG, "ScreenCaptureService destroyed")
    }
    
    private fun stopCapture() {
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
        
        Log.d(TAG, "Screen capture stopped")
    }
}