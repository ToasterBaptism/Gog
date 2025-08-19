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
import android.graphics.BitmapFactory
import androidx.core.content.ContextCompat

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
    
    // Template matching for ball detection
    private var ballTemplate: Bitmap? = null
    
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
        loadBallTemplate()
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
            // 🔧 FIX: Force landscape mode for Rocket League (game is always landscape)
            // The display metrics might return portrait dimensions due to Android quirks
            if (width < height) {
                // Swap dimensions if detected as portrait (likely wrong for RL)
                screenWidth = height
                screenHeight = width
                isLandscapeMode = true
                Log.w(TAG, "🔄 ORIENTATION FIX: Swapped dimensions ${width}x${height} -> ${screenWidth}x${screenHeight}")
            } else {
                screenWidth = width
                screenHeight = height
                isLandscapeMode = width > height
            }
            
            Log.d(TAG, "🖥️ Display: ${width}x${height}, density: $density, rotation: $rotation")
            Log.d(TAG, "📱 Corrected: ${screenWidth}x${screenHeight}")
            Log.d(TAG, "📱 Orientation: ${if (isLandscapeMode) "LANDSCAPE" else "PORTRAIT"} (forced for RL)")
            
            // Use RGBA_8888 for stability (avoid complex YUV conversion crashes)
            val format = PixelFormat.RGBA_8888
            
            Log.d(TAG, "Creating ImageReader with format: $format")
            imageReader = ImageReader.newInstance(screenWidth, screenHeight, format, 2) // Use corrected dimensions
            imageReader?.setOnImageAvailableListener(imageAvailableListener, backgroundHandler)
            
            Log.d(TAG, "Creating VirtualDisplay...")
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                screenWidth, screenHeight, density, // Use corrected dimensions
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
            
            // 🔍 REDUCED LOGGING: Only log coordinate analysis occasionally
            if (System.currentTimeMillis() % 5000 < 100) { // Log every ~5 seconds
                Log.d(TAG, "🔍 COORDINATE SPACE ANALYSIS:")
                Log.d(TAG, "📱 Screen dimensions: ${screenWidth}x${screenHeight}")
                Log.d(TAG, "🖼️ Bitmap dimensions: ${width}x${height}")
                Log.d(TAG, "📊 Dimension ratio: ${width.toFloat()/screenWidth} x ${height.toFloat()/screenHeight}")
                Log.d(TAG, "🎯 Landscape mode: $isLandscapeMode")
                
                // Check if there's a dimension mismatch that could explain coordinate issues
                val widthRatio = width.toFloat() / screenWidth
                val heightRatio = height.toFloat() / screenHeight
                if (abs(widthRatio - 1.0f) > 0.01f || abs(heightRatio - 1.0f) > 0.01f) {
                    Log.w(TAG, "⚠️ COORDINATE MISMATCH DETECTED!")
                    Log.w(TAG, "⚠️ Bitmap size (${width}x${height}) != Screen size (${screenWidth}x${screenHeight})")
                    Log.w(TAG, "⚠️ This could explain coordinate transformation issues!")
                }
            }
            
            // Adjust search area based on orientation - FOCUSED on center for performance
            val (searchStartX, searchEndX, searchStartY, searchEndY) = if (isLandscapeMode) {
                // In landscape: focus on center area where ball is most likely
                val searchStartX = (width * 0.15).toInt() // Skip left 15% - focus on center
                val searchEndX = (width * 0.85).toInt() // Skip right 15% - focus on center
                val searchStartY = (height * 0.15).toInt() // Skip top 15%
                val searchEndY = (height * 0.85).toInt() // Skip bottom 15%
                Tuple4(searchStartX, searchEndX, searchStartY, searchEndY)
            } else {
                // In portrait: search central area
                val searchStartX = (width * 0.15).toInt() // Skip left 15%
                val searchEndX = (width * 0.85).toInt() // Skip right 15%
                val searchStartY = (height * 0.15).toInt() // Skip top 15%
                val searchEndY = (height * 0.75).toInt() // Search upper 75% of screen
                Tuple4(searchStartX, searchEndX, searchStartY, searchEndY)
            }
            
            Log.d(TAG, "🔍 Search area: X($searchStartX-$searchEndX), Y($searchStartY-$searchEndY) [${if (isLandscapeMode) "LANDSCAPE" else "PORTRAIT"}]")
            
            // 🎯 PRIMARY: Template matching for specific ball detection
            val templateMatches = detectBallUsingTemplate(bitmap, searchStartX, searchEndX, searchStartY, searchEndY)
            
            // 🔍 FALLBACK: Shape analysis if template matching fails
            val finalCircles = if (templateMatches.isNotEmpty()) {
                Log.d(TAG, "🎯 Using template matching results: ${templateMatches.size} matches")
                templateMatches
            } else {
                Log.d(TAG, "🔄 Template matching failed, trying shape analysis...")
                val shapeMatches = detectCircularObjects(bitmap, searchStartX, searchEndX, searchStartY, searchEndY)
                if (shapeMatches.isEmpty()) {
                    Log.d(TAG, "🔄 No circles found with any method")
                }
                shapeMatches
            }
            
            if (finalCircles.isNotEmpty()) {
                // Find the best ball candidate based on size, position, and circularity
                val bestBall = findBestBallCandidate(finalCircles, width, height)
                
                if (bestBall != null) {
                    val bestX = bestBall.x
                    val bestY = bestBall.y
                    val confidence = bestBall.confidence
                    Log.d(TAG, "🎯 BALL DETECTED: Position ($bestX, $bestY), Confidence: ${"%.2f".format(confidence)}")
                    Log.d(TAG, "📱 Screen info: ${screenWidth}x${screenHeight}, landscape: $isLandscapeMode")
                    Log.d(TAG, "🖼️ Image dimensions: ${width}x${height}")
                    Log.d(TAG, "📊 Ball position relative to image: X=${"%.1f".format(bestX/width*100)}%, Y=${"%.1f".format(bestY/height*100)}%")
                    Log.d(TAG, "🔍 Search area used: ($searchStartX, $searchStartY) to ($searchEndX, $searchEndY)")
                    
                    // 🔍 CRITICAL: Show coordinate transformation details
                    val normalizedX = bestX / width
                    val normalizedY = bestY / height
                    val screenX = normalizedX * screenWidth
                    val screenY = normalizedY * screenHeight
                    Log.d(TAG, "🔄 COORDINATE TRANSFORMATION:")
                    Log.d(TAG, "🔄 Bitmap coords: ($bestX, $bestY)")
                    Log.d(TAG, "🔄 Normalized: (${"%.3f".format(normalizedX)}, ${"%.3f".format(normalizedY)})")
                    Log.d(TAG, "🔄 Screen coords: (${"%.1f".format(screenX)}, ${"%.1f".format(screenY)})")
                    Log.d(TAG, "🔄 Expected overlay position: (${"%.1f".format(screenX)}, ${"%.1f".format(screenY)})")
                    
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
                            if (speed > 15) { // LOWERED speed threshold for more responsive detection
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
    
    // Load ball template for template matching
    private fun loadBallTemplate() {
        try {
            // For now, create a simple template - you'll replace this with actual ball image
            ballTemplate = createSimpleBallTemplate()
            Log.d(TAG, "🎯 Ball template loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load ball template", e)
        }
    }
    
    // Create a simple ball template (placeholder - replace with actual ball image)
    private fun createSimpleBallTemplate(): Bitmap {
        val size = 60 // Template size
        val template = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val centerX = size / 2
        val centerY = size / 2
        val radius = size / 3
        
        // Create a simple gray circle template
        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = x - centerX
                val dy = y - centerY
                val distance = sqrt((dx * dx + dy * dy).toFloat())
                
                if (distance <= radius) {
                    // Gray metallic ball color
                    val intensity = (200 - (distance / radius * 50)).toInt().coerceIn(150, 200)
                    val color = (0xFF shl 24) or (intensity shl 16) or (intensity shl 8) or intensity
                    template.setPixel(x, y, color)
                } else {
                    template.setPixel(x, y, 0x00000000) // Transparent
                }
            }
        }
        
        return template
    }
    
    // Template matching for ball detection
    private fun detectBallUsingTemplate(bitmap: Bitmap, startX: Int, endX: Int, startY: Int, endY: Int): List<CircleCandidate> {
        val candidates = mutableListOf<CircleCandidate>()
        val template = ballTemplate ?: return candidates
        
        try {
            val templateWidth = template.width
            val templateHeight = template.height
            val threshold = 0.7f // Template matching threshold
            
            Log.d(TAG, "🎯 Template matching: ${templateWidth}x${templateHeight} template")
            
            // Search for template matches
            val searchStep = 8 // Faster search
            for (y in startY until (endY - templateHeight) step searchStep) {
                for (x in startX until (endX - templateWidth) step searchStep) {
                    val similarity = calculateTemplateSimilarity(bitmap, template, x, y)
                    
                    if (similarity > threshold) {
                        val centerX = x + templateWidth / 2
                        val centerY = y + templateHeight / 2
                        val radius = templateWidth / 2
                        
                        candidates.add(CircleCandidate(
                            x = centerX.toFloat(),
                            y = centerY.toFloat(),
                            radius = radius.toFloat(),
                            confidence = similarity,
                            edgeStrength = 0.8f // High edge strength for template matches
                        ))
                        
                        Log.d(TAG, "🎯 Template match: ($centerX,$centerY) similarity=${"%.3f".format(similarity)}")
                    }
                }
            }
            
            Log.d(TAG, "🎯 Template matching found ${candidates.size} candidates")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in template matching", e)
        }
        
        return candidates
    }
    
    // Calculate similarity between template and image region
    private fun calculateTemplateSimilarity(bitmap: Bitmap, template: Bitmap, startX: Int, startY: Int): Float {
        val templateWidth = template.width
        val templateHeight = template.height
        var totalDifference = 0f
        var pixelCount = 0
        
        for (ty in 0 until templateHeight) {
            for (tx in 0 until templateWidth) {
                val bitmapX = startX + tx
                val bitmapY = startY + ty
                
                if (bitmapX < bitmap.width && bitmapY < bitmap.height) {
                    val templatePixel = template.getPixel(tx, ty)
                    val bitmapPixel = bitmap.getPixel(bitmapX, bitmapY)
                    
                    // Skip transparent template pixels
                    if ((templatePixel ushr 24) > 0) {
                        val templateGray = getPixelBrightness(templatePixel)
                        val bitmapGray = getPixelBrightness(bitmapPixel)
                        
                        val difference = abs(templateGray - bitmapGray)
                        totalDifference += difference
                        pixelCount++
                    }
                }
            }
        }
        
        if (pixelCount == 0) return 0f
        
        val avgDifference = totalDifference / pixelCount
        val similarity = 1f - (avgDifference / 255f) // Convert to similarity (0-1)
        
        return similarity.coerceIn(0f, 1f)
    }
    
    // Detect circular objects using edge detection and shape analysis
    private fun detectCircularObjects(bitmap: Bitmap, startX: Int, endX: Int, startY: Int, endY: Int): List<CircleCandidate> {
        val circles = mutableListOf<CircleCandidate>()
        
        try {
            // Expected ball size range (in pixels) - FOCUSED for performance
            val minRadius = 30 // Increased minimum - ball is large
            val maxRadius = 100 // Reasonable maximum for performance
            val searchStep = 12 // Increased step for much better performance
            
            Log.d(TAG, "🔍 Searching for circles in area ($startX,$startY) to ($endX,$endY)")
            
            // Grid search for circular patterns
            for (centerY in startY until endY step searchStep) {
                for (centerX in startX until endX step searchStep) {
                    // Test different radii
                    for (testRadius in minRadius..maxRadius step 4) { // Smaller radius steps
                        val circleScore = analyzeCircularPattern(bitmap, centerX, centerY, testRadius)
                        
                        if (circleScore > 0.4f) { // MUCH HIGHER circularity threshold for selectivity
                            val edgeStrength = calculateEdgeStrength(bitmap, centerX, centerY, testRadius)
                            val confidence = (circleScore * 0.7f + edgeStrength * 0.3f).coerceAtMost(1f)
                            
                            if (confidence > 0.6f) { // MUCH HIGHER confidence threshold for selectivity
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
            
            Log.d(TAG, "🔍 Found ${circles.size} circle candidates in search area ${endX-startX}x${endY-startY}")
            
            // Log top candidates for debugging
            if (circles.isNotEmpty()) {
                val topCandidates = circles.sortedByDescending { it.confidence }.take(3)
                topCandidates.forEachIndexed { index, candidate ->
                    Log.d(TAG, "🏆 Top candidate #${index+1}: (${candidate.x.toInt()},${candidate.y.toInt()}) r=${candidate.radius.toInt()}, conf=${"%.3f".format(candidate.confidence)}")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in circle detection", e)
        }
        
        return circles
    }
    
    // Fallback detection: Find brightest circular areas (simple approach)
    private fun detectBrightestCircularAreas(bitmap: Bitmap, startX: Int, endX: Int, startY: Int, endY: Int): List<CircleCandidate> {
        val circles = mutableListOf<CircleCandidate>()
        
        try {
            val searchStep = 12 // Larger step for faster fallback
            val testRadii = listOf(20, 30, 40, 50, 60, 80, 100) // Common ball sizes
            
            Log.d(TAG, "🔄 Fallback detection: Looking for bright circular areas...")
            
            for (centerY in startY until endY step searchStep) {
                for (centerX in startX until endX step searchStep) {
                    for (testRadius in testRadii) {
                        if (isValidPixel(bitmap, centerX - testRadius, centerY - testRadius) &&
                            isValidPixel(bitmap, centerX + testRadius, centerY + testRadius)) {
                            
                            // Simple brightness check in circular area
                            var totalBrightness = 0f
                            var pixelCount = 0
                            
                            // Sample pixels in a circular pattern
                            for (i in 0 until 8) {
                                val angle = (i * 2 * Math.PI / 8)
                                val x = (centerX + testRadius * 0.7 * cos(angle)).toInt()
                                val y = (centerY + testRadius * 0.7 * sin(angle)).toInt()
                                
                                if (isValidPixel(bitmap, x, y)) {
                                    totalBrightness += getPixelBrightness(bitmap.getPixel(x, y))
                                    pixelCount++
                                }
                            }
                            
                            if (pixelCount > 0) {
                                val avgBrightness = totalBrightness / pixelCount
                                // Look for moderately bright areas (ball is grayish)
                                if (avgBrightness > 80 && avgBrightness < 200) {
                                    circles.add(CircleCandidate(
                                        x = centerX.toFloat(),
                                        y = centerY.toFloat(),
                                        radius = testRadius.toFloat(),
                                        confidence = (avgBrightness / 255f) * 0.5f, // Lower confidence for fallback
                                        edgeStrength = 0.3f
                                    ))
                                    
                                    Log.d(TAG, "🔄 Fallback candidate: ($centerX,$centerY) r=$testRadius, brightness=${"%.1f".format(avgBrightness)}")
                                }
                            }
                        }
                    }
                }
            }
            
            Log.d(TAG, "🔄 Fallback found ${circles.size} candidates")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in fallback detection", e)
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
                    
                    // Look for brightness difference (edge) - LOWERED threshold for more sensitivity
                    if (abs(innerBrightness - outerBrightness) > 20) {
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
            
            // Prefer larger circles (ball appears large in the image)
            val idealRadius = 60f // Increased ideal radius for larger balls
            val radiusDiff = abs(circle.radius - idealRadius)
            val radiusBonus = 1f - (radiusDiff / idealRadius) * 0.15f // Reduced penalty for size difference
            score *= radiusBonus.coerceAtLeast(0.6f) // Less harsh penalty
            
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