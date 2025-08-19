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
            
            Log.d(TAG, "Display: ${width}x${height}, density: $density")
            
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
            // Enhanced color-based ball detection for multiple ball colors
            val width = bitmap.width
            val height = bitmap.height
            
            // Sample pixels to find ball-colored circular objects
            var bestX = -1f
            var bestY = -1f
            var maxBallPixels = 0
            
            // Grid search for ball-colored regions
            val gridSize = 50
            for (y in 0 until height step gridSize) {
                for (x in 0 until width step gridSize) {
                    val ballPixelCount = countBallPixelsInRegion(bitmap, x, y, gridSize)
                    if (ballPixelCount > maxBallPixels) {
                        maxBallPixels = ballPixelCount
                        bestX = x.toFloat() + gridSize / 2f
                        bestY = y.toFloat() + gridSize / 2f
                    }
                }
            }
            
            if (maxBallPixels > 10) { // Found potential ball
                Log.d(TAG, "Ball detected at ($bestX, $bestY) with $maxBallPixels ball-colored pixels")
                FrameResult(
                    ball = Detection(
                        cx = bestX / width, // Normalize to 0-1
                        cy = bestY / height,
                        r = 0.05f, // Estimated radius
                        conf = (maxBallPixels / 100f).coerceAtMost(1f)
                    ),
                    timestampNanos = System.nanoTime()
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Simple ball detection failed", e)
            null
        }
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
        
        // Method 2: Gray/Silver balls (like in your screenshot)
        val isGraySilver = red > 120 && green > 120 && blue > 120 && 
                          red < 200 && green < 200 && blue < 200 &&
                          Math.abs(red - green) < 30 && Math.abs(red - blue) < 30 && Math.abs(green - blue) < 30
        
        // Method 3: White/Bright balls
        val isWhiteBright = red > 200 && green > 200 && blue > 200
        
        // Method 4: Yellow/Golden balls
        val isYellowGold = red > 180 && green > 150 && blue < 120 && red > green && green > blue
        
        return isOrangeRed || isGraySilver || isWhiteBright || isYellowGold
    }
    
    private var lastBallDetectionTime = 0L
    private var ballDetectionCount = 0
    
    private fun updateNotificationWithBallDetection(ball: Detection?) {
        ball?.let {
            val currentTime = System.currentTimeMillis()
            ballDetectionCount++
            
            // Update notification every 2 seconds to show ball detection is working
            if (currentTime - lastBallDetectionTime > 2000) {
                lastBallDetectionTime = currentTime
                
                val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("RL Sideswipe Access - BALL DETECTED!")
                    .setContentText("🎯 Ball found at (${(it.cx * 100).toInt()}%, ${(it.cy * 100).toInt()}%) - Confidence: ${(it.conf * 100).toInt()}% - Count: $ballDetectionCount")
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