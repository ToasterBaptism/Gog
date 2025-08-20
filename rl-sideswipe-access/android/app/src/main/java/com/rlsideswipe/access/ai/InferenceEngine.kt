package com.rlsideswipe.access.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.Log
import org.tensorflow.lite.Interpreter
// import org.tensorflow.lite.gpu.CompatibilityList
// import org.tensorflow.lite.gpu.GpuDelegate
// import org.tensorflow.lite.nnapi.NnApiDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.*
import com.rlsideswipe.access.util.OpenCVUtils

data class Detection(
    val cx: Float,
    val cy: Float,
    val r: Float,
    val conf: Float
)

data class FrameResult(
    val ball: Detection?,
    val timestampNanos: Long
)

interface InferenceEngine {
    fun warmup()
    fun infer(frame: Bitmap): FrameResult
    fun close()
}



class TFLiteInferenceEngine(private val context: Context) : InferenceEngine {
    
    private var interpreter: Interpreter? = null
    private var inputBuffer: ByteBuffer? = null
    private var outputBuffer: ByteBuffer? = null
    private var hanningWindow: FloatArray? = null
    
    companion object {
        private const val TAG = "TFLiteInferenceEngine"
        private const val MODEL_FILE = "rl_sideswipe_ball_v1.tflite"
        private const val INPUT_SIZE = 320
        private const val CHANNELS = 3
        private const val BYTES_PER_CHANNEL = 4 // Float32
        private const val CONFIDENCE_THRESHOLD = 0.25f // Lowered for better detection
        private const val OUTPUT_SIZE = 5 // [x, y, w, h, confidence]
    }
    
    init {
        try {
            Log.d(TAG, "🤖 Initializing TensorFlow Lite inference engine...")
            loadModel()
            initializeBuffers()
            createHanningWindow()
            Log.i(TAG, "✅ TensorFlow Lite inference engine initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize TensorFlow Lite: ${e.message}", e)
            // Clean up any partially initialized resources
            cleanup()
            throw RuntimeException("TensorFlow Lite initialization failed: ${e.message}", e)
        }
    }
    
    private fun cleanup() {
        try {
            interpreter?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error during cleanup", e)
        }
        interpreter = null
        inputBuffer = null
        outputBuffer = null
        hanningWindow = null
    }
    
    private fun loadModel() {
        try {
            Log.d(TAG, "📁 Loading TensorFlow Lite model...")
            val modelBuffer = loadModelFile()
            Log.d(TAG, "📁 Model file loaded, size: ${modelBuffer.remaining()} bytes")
            
            val options = Interpreter.Options()
            
            // Set number of threads for CPU inference
            options.setNumThreads(4)
            Log.d(TAG, "🖥️ Using CPU inference with 4 threads")
            
            interpreter = Interpreter(modelBuffer, options)
            Log.d(TAG, "✅ TensorFlow Lite interpreter created successfully")
            
            // Verify model input/output shapes
            val inputShape = interpreter?.getInputTensor(0)?.shape()
            val outputShape = interpreter?.getOutputTensor(0)?.shape()
            Log.d(TAG, "📊 Model input shape: ${inputShape?.contentToString()}")
            Log.d(TAG, "📊 Model output shape: ${outputShape?.contentToString()}")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to load TensorFlow Lite model", e)
            throw e // Re-throw to trigger fallback
        }
    }
    

    
    private fun initializeBuffers() {
        val inputSize = INPUT_SIZE * INPUT_SIZE * CHANNELS * BYTES_PER_CHANNEL
        inputBuffer = ByteBuffer.allocateDirect(inputSize).apply {
            order(ByteOrder.nativeOrder())
        }
        
        val outputSize = OUTPUT_SIZE * BYTES_PER_CHANNEL
        outputBuffer = ByteBuffer.allocateDirect(outputSize).apply {
            order(ByteOrder.nativeOrder())
        }
    }
    
    private fun createHanningWindow() {
        val size = INPUT_SIZE
        val center = size / 2f
        val radius = center * 0.9f // Slightly smaller than full radius
        
        hanningWindow = FloatArray(size * size) { i ->
            val x = i % size
            val y = i / size
            val dx = x - center
            val dy = y - center
            val distance = sqrt(dx * dx + dy * dy)
            
            if (distance <= radius) {
                val normalized = distance / radius
                // Hanning window: 0.5 * (1 + cos(π * normalized))
                (0.5 * (1.0 + cos(PI * normalized))).toFloat()
            } else {
                0f
            }
        }
    }
    
    private fun loadModelFile(): MappedByteBuffer {
        try {
            Log.d(TAG, "📂 Opening model file: $MODEL_FILE")
            val assetFileDescriptor = context.assets.openFd(MODEL_FILE)
            Log.d(TAG, "📂 Asset file descriptor: length=${assetFileDescriptor.declaredLength}, offset=${assetFileDescriptor.startOffset}")
            
            val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            
            if (declaredLength <= 0) {
                throw IllegalStateException("Model file is empty or invalid: length=$declaredLength")
            }
            
            val buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            Log.d(TAG, "📂 Model file mapped successfully: ${buffer.remaining()} bytes")
            return buffer
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to load model file: $MODEL_FILE", e)
            throw e
        }
    }
    
    override fun warmup() {
        try {
            Log.d(TAG, "🔥 Warming up TensorFlow Lite model...")
            // Create dummy input for warmup
            val dummyInput = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
            repeat(3) { // Multiple warmup runs for better performance
                infer(dummyInput)
            }
            dummyInput.recycle()
            Log.d(TAG, "✅ Warmup completed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Warmup failed", e)
        }
    }
    
    override fun infer(frame: Bitmap): FrameResult {
        val timestampNanos = System.nanoTime()
        
        Log.d(TAG, "🔍 INFER: Starting inference on ${frame.width}x${frame.height} frame")
        
        return try {
            val interpreter = this.interpreter
            val inputBuffer = this.inputBuffer
            val outputBuffer = this.outputBuffer
            
            if (interpreter == null || inputBuffer == null || outputBuffer == null) {
                Log.w(TAG, "⚠️ Inference engine not properly initialized")
                return FrameResult(null, timestampNanos)
            }
            
            // Preprocess the frame with quality analysis
            val preprocessedBitmap = preprocessFrame(frame)
            
            // Log preprocessing quality metrics (every 30 frames to avoid spam)
            if (System.currentTimeMillis() % 30000 < 100) { // Roughly every 30 seconds
                logPreprocessingQuality(frame, preprocessedBitmap)
            }
            
            // Convert bitmap to input buffer
            bitmapToBuffer(preprocessedBitmap, inputBuffer)
            
            // Run inference
            inputBuffer.rewind()
            outputBuffer.rewind()
            
            val startTime = System.nanoTime()
            interpreter.run(inputBuffer, outputBuffer)
            val inferenceTime = (System.nanoTime() - startTime) / 1_000_000 // Convert to ms
            
            // Parse output with proper coordinate scaling
            val detection = parseOutput(outputBuffer, frame.width, frame.height)
            
            if (inferenceTime > 50) { // Log if inference takes too long
                Log.w(TAG, "⚠️ Slow inference: ${inferenceTime}ms")
            }
            
            if (detection != null) {
                Log.d(TAG, "🎯 Ball detected: (${detection.cx}, ${detection.cy}) r=${detection.r} conf=${detection.conf} [${inferenceTime}ms]")
            } else {
                Log.d(TAG, "❌ No ball detected above threshold ${CONFIDENCE_THRESHOLD} [${inferenceTime}ms]")
            }
            
            preprocessedBitmap.recycle()
            
            FrameResult(detection, timestampNanos)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Inference failed", e)
            FrameResult(null, timestampNanos)
        }
    }
    
    private fun preprocessFrame(frame: Bitmap): Bitmap {
        try {
            Log.d(TAG, "🔄 Starting advanced preprocessing with OpenCV-style operations")
            
            // 1. Apply adaptive OpenCV-style preprocessing for better ball detection
            val preprocessed = OpenCVUtils.adaptivePreprocessForDetection(frame)
            Log.d(TAG, "✅ Adaptive OpenCV preprocessing complete: grayscale + adaptive contrast + adaptive blur")
            
            // 2. Resize to model input size while maintaining preprocessing benefits
            val scaledBitmap = Bitmap.createScaledBitmap(preprocessed, INPUT_SIZE, INPUT_SIZE, true)
            preprocessed.recycle() // Clean up intermediate bitmap
            
            // 3. Apply Hanning window to reduce edge artifacts (additional enhancement)
            val result = applyHanningWindow(scaledBitmap)
            scaledBitmap.recycle() // Clean up intermediate bitmap
            
            Log.d(TAG, "🎯 Advanced preprocessing pipeline complete")
            return result
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in advanced preprocessing, falling back to basic", e)
            // Fallback to basic preprocessing if OpenCV processing fails
            val scaledBitmap = Bitmap.createScaledBitmap(frame, INPUT_SIZE, INPUT_SIZE, true)
            return applyHanningWindow(scaledBitmap)
        }
    }
    
    private fun applyHanningWindow(bitmap: Bitmap): Bitmap {
        val hanningWindow = this.hanningWindow ?: return bitmap
        
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        result.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val alpha = (pixel shr 24) and 0xFF
            val red = ((pixel shr 16) and 0xFF)
            val green = ((pixel shr 8) and 0xFF)
            val blue = (pixel and 0xFF)
            
            val windowValue = hanningWindow[i]
            val newRed = (red * windowValue).toInt().coerceIn(0, 255)
            val newGreen = (green * windowValue).toInt().coerceIn(0, 255)
            val newBlue = (blue * windowValue).toInt().coerceIn(0, 255)
            
            pixels[i] = (alpha shl 24) or (newRed shl 16) or (newGreen shl 8) or newBlue
        }
        
        result.setPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        return result
    }
    
    private fun bitmapToBuffer(bitmap: Bitmap, buffer: ByteBuffer) {
        buffer.rewind()
        
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        
        for (pixel in pixels) {
            // Extract RGB values and normalize to [0, 1]
            val red = ((pixel shr 16) and 0xFF) / 255.0f
            val green = ((pixel shr 8) and 0xFF) / 255.0f
            val blue = (pixel and 0xFF) / 255.0f
            
            // Store as float32 in RGB order
            buffer.putFloat(red)
            buffer.putFloat(green)
            buffer.putFloat(blue)
        }
    }
    
    private fun parseOutput(outputBuffer: ByteBuffer, originalWidth: Int, originalHeight: Int): Detection? {
        outputBuffer.rewind()
        
        Log.d(TAG, "🔍 PARSING: Reading output for ${originalWidth}x${originalHeight} frame")
        
        // Read output: [x, y, w, h, confidence]
        val x = outputBuffer.float
        val y = outputBuffer.float
        val w = outputBuffer.float
        val h = outputBuffer.float
        val confidence = outputBuffer.float
        
        Log.d(TAG, "🔍 RAW OUTPUT: x=$x, y=$y, w=$w, h=$h, conf=$confidence (threshold=$CONFIDENCE_THRESHOLD)")
        
        if (confidence < CONFIDENCE_THRESHOLD) {
            Log.d(TAG, "❌ Detection below confidence threshold: $confidence < $CONFIDENCE_THRESHOLD")
            return null
        }
        
        // Convert from normalized coordinates to original image coordinates
        val scaleX = originalWidth.toFloat() / INPUT_SIZE
        val scaleY = originalHeight.toFloat() / INPUT_SIZE
        
        val centerX = x * originalWidth
        val centerY = y * originalHeight
        val radius = (max(w, h) * max(originalWidth, originalHeight)) / 2f
        
        Log.d(TAG, "🎯 SCALED DETECTION: centerX=$centerX, centerY=$centerY, radius=$radius, conf=$confidence")
        Log.d(TAG, "📏 SCALING: scaleX=$scaleX, scaleY=$scaleY, originalSize=${originalWidth}x${originalHeight}")
        
        return Detection(
            cx = centerX,
            cy = centerY,
            r = radius,
            conf = confidence
        )
    }
    
    override fun close() {
        interpreter?.close()
        
        interpreter = null
        inputBuffer = null
        outputBuffer = null
        hanningWindow = null
        
        Log.d(TAG, "🔒 Inference engine closed")
    }
    
    /**
     * Log preprocessing quality metrics to help analyze the effectiveness of OpenCV operations
     */
    private fun logPreprocessingQuality(original: Bitmap, processed: Bitmap) {
        try {
            // Calculate sharpness for both images
            val originalSharpness = OpenCVUtils.calculateSharpness(original)
            val processedSharpness = OpenCVUtils.calculateSharpness(processed)
            
            // Calculate improvement ratio
            val sharpnessRatio = if (originalSharpness > 0) processedSharpness / originalSharpness else 0f
            
            Log.i(TAG, "📊 PREPROCESSING QUALITY ANALYSIS:")
            Log.i(TAG, "   📐 Original size: ${original.width}x${original.height}")
            Log.i(TAG, "   📐 Processed size: ${processed.width}x${processed.height}")
            Log.i(TAG, "   🔍 Original sharpness: %.2f".format(originalSharpness))
            Log.i(TAG, "   🔍 Processed sharpness: %.2f".format(processedSharpness))
            Log.i(TAG, "   📈 Sharpness ratio: %.2fx".format(sharpnessRatio))
            
            if (sharpnessRatio > 1.1f) {
                Log.i(TAG, "   ✅ Preprocessing IMPROVED image quality")
            } else if (sharpnessRatio < 0.9f) {
                Log.w(TAG, "   ⚠️ Preprocessing may have REDUCED image quality")
            } else {
                Log.i(TAG, "   ➡️ Preprocessing maintained image quality")
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to calculate preprocessing quality metrics", e)
        }
    }
}

/**
 * Stub implementation for when TensorFlow Lite fails to initialize
 * This prevents crashes and allows the service to continue running
 */
class StubInferenceEngine : InferenceEngine {
    
    companion object {
        private const val TAG = "StubInferenceEngine"
    }
    
    init {
        Log.w(TAG, "Using stub inference engine - no AI processing will occur")
    }
    
    override fun warmup() {
        Log.d(TAG, "Stub warmup completed")
    }
    
    override fun infer(frame: Bitmap): FrameResult {
        // Return empty result - no detection
        return FrameResult(null, System.nanoTime())
    }
    
    override fun close() {
        Log.d(TAG, "Stub inference engine closed")
    }
}