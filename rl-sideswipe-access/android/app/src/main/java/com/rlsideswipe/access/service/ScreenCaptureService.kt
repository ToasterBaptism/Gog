package com.rlsideswipe.access.service

import android.app.*
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
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
import com.rlsideswipe.access.ai.TrajectoryPredictor
import com.rlsideswipe.access.ai.KalmanTrajectoryPredictor
import com.rlsideswipe.access.util.BitmapUtils

class ScreenCaptureService : Service() {
    
    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "screen_capture_channel"
        private const val TARGET_FPS = 20
        private const val FRAME_INTERVAL_MS = 1000L / TARGET_FPS
    }
    
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var inferenceEngine: InferenceEngine? = null
    private var trajectoryPredictor: TrajectoryPredictor? = null
    
    private val handler = Handler(Looper.getMainLooper())
    private var lastFrameTime = 0L
    
    // LiveData for sharing results with overlay
    val frameResults = MutableLiveData<FrameResult?>()
    val trajectoryPoints = MutableLiveData<List<com.rlsideswipe.access.ai.TrajectoryPoint>?>()
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        inferenceEngine = TFLiteInferenceEngine(this)
        trajectoryPredictor = KalmanTrajectoryPredictor()
        inferenceEngine?.warmup()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val captureIntent = intent?.getParcelableExtra<Intent>("captureIntent")
        if (captureIntent != null) {
            startCapture(captureIntent)
        }
        
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        
        return START_STICKY
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
        val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mediaProjectionManager.getMediaProjection(Activity.RESULT_OK, captureIntent)
        
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        val density = displayMetrics.densityDpi
        
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader?.setOnImageAvailableListener(imageAvailableListener, handler)
        
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null, null
        )
        
        Log.d(TAG, "Screen capture started: ${width}x${height}")
    }
    
    private val imageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        val currentTime = System.currentTimeMillis()
        
        // Throttle frame processing
        if (currentTime - lastFrameTime < FRAME_INTERVAL_MS) {
            reader.acquireLatestImage()?.close()
            return@OnImageAvailableListener
        }
        
        lastFrameTime = currentTime
        
        val image = reader.acquireLatestImage()
        if (image != null) {
            processFrame(image)
            image.close()
        }
    }
    
    private fun processFrame(image: Image) {
        try {
            val bitmap = BitmapUtils.imageToBitmap(image)
            if (bitmap != null) {
                val frameResult = inferenceEngine?.infer(bitmap)
                if (frameResult != null) {
                    frameResults.postValue(frameResult)
                    
                    val trajectory = trajectoryPredictor?.update(frameResult.ball, System.currentTimeMillis())
                    if (trajectory != null) {
                        trajectoryPoints.postValue(trajectory)
                    }
                }
                bitmap.recycle()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopCapture()
        inferenceEngine?.close()
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