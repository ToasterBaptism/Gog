package com.rlsideswipe.access.service

import android.app.*
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
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
import com.rlsideswipe.access.ai.FrameResult
import com.rlsideswipe.access.ai.InferenceEngine
import com.rlsideswipe.access.ai.TFLiteInferenceEngine
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
            startForeground(NOTIFICATION_ID, notification)
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
    
    override fun onBind(intent: Intent?): IBinder? = null
    
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
            
            // Use YUV_420_888 for better performance, fallback to RGBA_8888
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                ImageFormat.YUV_420_888
            } else {
                PixelFormat.RGBA_8888
            }
            
            Log.d(TAG, "Creating ImageReader with format: $format")
            imageReader = ImageReader.newInstance(width, height, format, 3) // Increased buffer size
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
                
                // Initialize inference engine with comprehensive error handling
                inferenceEngine = try {
                    Log.d(TAG, "Attempting to create TensorFlow Lite inference engine...")
                    val engine = TFLiteInferenceEngine(this@ScreenCaptureService)
                    Log.i(TAG, "TensorFlow Lite inference engine created successfully")
                    engine
                } catch (e: Exception) {
                    Log.w(TAG, "TensorFlow Lite engine failed, falling back to stub: ${e.message}", e)
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
        val currentTime = System.currentTimeMillis()
        
        // Skip frame if we're still processing the previous one
        if (isProcessingFrame) {
            reader.acquireLatestImage()?.close()
            droppedFrames++
            return@OnImageAvailableListener
        }
        
        // Throttle frame processing to target FPS
        if (currentTime - lastFrameTime < FRAME_INTERVAL_MS) {
            reader.acquireLatestImage()?.close()
            return@OnImageAvailableListener
        }
        
        lastFrameTime = currentTime
        frameCount++
        
        val image = reader.acquireLatestImage()
        if (image != null) {
            isProcessingFrame = true
            processFrame(image)
            image.close()
        }
        
        // Log performance metrics periodically
        if (currentTime - lastPerformanceLog > PERFORMANCE_LOG_INTERVAL) {
            logPerformanceMetrics(currentTime)
        }
    }
    
    private fun processFrame(image: Image) {
        val startTime = System.nanoTime()
        
        try {
            val bitmap = BitmapUtils.imageToBitmap(image)
            if (bitmap != null) {
                // Downsample large images for better performance
                val processedBitmap = if (bitmap.width > 1080 || bitmap.height > 1080) {
                    val downsampled = BitmapUtils.downsampleBitmap(bitmap, 1080)
                    bitmap.recycle()
                    downsampled
                } else {
                    bitmap
                }
                
                val frameResult = inferenceEngine?.infer(processedBitmap)
                if (frameResult != null) {
                    // Post results on main thread
                    mainHandler.post {
                        frameResults.value = frameResult
                        
                        val trajectory = trajectoryPredictor?.update(frameResult.ball, System.currentTimeMillis())
                        if (trajectory != null) {
                            trajectoryPoints.value = trajectory
                        }
                    }
                }
                processedBitmap.recycle()
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