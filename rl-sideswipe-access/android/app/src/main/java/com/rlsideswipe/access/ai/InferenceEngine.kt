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
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
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
    private var gpuDelegate: GpuDelegate? = null
    private var nnApiDelegate: NnApiDelegate? = null
    private var inputBuffer: ByteBuffer? = null
    private var outputBuffer: ByteBuffer? = null
    private var hanningWindow: FloatArray? = null
    
    companion object {
        private const val TAG = "InferenceEngine"
        private const val MODEL_FILE = "rl_sideswipe_ball_v1.tflite"
        private const val INPUT_SIZE = 320
        private const val CHANNELS = 3
        private const val BYTES_PER_CHANNEL = 4 // Float32
        private const val CONFIDENCE_THRESHOLD = 0.5f
        private const val OUTPUT_SIZE = 5 // [x, y, w, h, confidence]
    }
    
    init {
        try {
            loadModel()
            initializeBuffers()
            createHanningWindow()
            Log.d(TAG, "TFLiteInferenceEngine initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TFLiteInferenceEngine", e)
            throw e // Re-throw to trigger fallback to StubInferenceEngine
        }
    }
    
    private fun loadModel() {
        try {
            Log.d(TAG, "Loading TensorFlow Lite model...")
            val modelBuffer = loadModelFile()
            Log.d(TAG, "Model file loaded, size: ${modelBuffer.remaining()} bytes")
            
            val options = Interpreter.Options()
            
            // Set number of threads for CPU inference
            options.setNumThreads(4)
            
            // Try GPU delegate first
            val compatibilityList = CompatibilityList()
            if (compatibilityList.isDelegateSupportedOnThisDevice) {
                try {
                    gpuDelegate = GpuDelegate()
                    options.addDelegate(gpuDelegate)
                    Log.d(TAG, "Using GPU delegate")
                } catch (e: Exception) {
                    Log.w(TAG, "GPU delegate failed, falling back", e)
                    gpuDelegate?.close()
                    gpuDelegate = null
                    tryNnApiDelegate(options)
                }
            } else {
                Log.d(TAG, "GPU delegate not supported, trying NNAPI")
                tryNnApiDelegate(options)
            }
            
            interpreter = Interpreter(modelBuffer, options)
            Log.d(TAG, "TensorFlow Lite interpreter created successfully")
            
            // Verify model input/output shapes
            val inputShape = interpreter?.getInputTensor(0)?.shape()
            val outputShape = interpreter?.getOutputTensor(0)?.shape()
            Log.d(TAG, "Model input shape: ${inputShape?.contentToString()}")
            Log.d(TAG, "Model output shape: ${outputShape?.contentToString()}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load TensorFlow Lite model", e)
            throw e // Re-throw to trigger fallback
        }
    }
    
    private fun tryNnApiDelegate(options: Interpreter.Options) {
        try {
            nnApiDelegate = NnApiDelegate()
            options.addDelegate(nnApiDelegate)
            Log.d(TAG, "Using NNAPI delegate")
        } catch (e: Exception) {
            Log.d(TAG, "Using CPU inference")
            nnApiDelegate?.close()
            nnApiDelegate = null
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
            Log.d(TAG, "Opening model file: $MODEL_FILE")
            val assetFileDescriptor = context.assets.openFd(MODEL_FILE)
            Log.d(TAG, "Asset file descriptor: length=${assetFileDescriptor.declaredLength}, offset=${assetFileDescriptor.startOffset}")
            
            val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            
            if (declaredLength <= 0) {
                throw IllegalStateException("Model file is empty or invalid: length=$declaredLength")
            }
            
            val buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            Log.d(TAG, "Model file mapped successfully: ${buffer.remaining()} bytes")
            return buffer
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model file: $MODEL_FILE", e)
            throw e
        }
    }
    
    override fun warmup() {
        try {
            // Create dummy input for warmup
            val dummyInput = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
            repeat(3) { // Multiple warmup runs for better performance
                infer(dummyInput)
            }
            dummyInput.recycle()
            Log.d(TAG, "Warmup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Warmup failed", e)
        }
    }
    
    override fun infer(frame: Bitmap): FrameResult {
        val timestampNanos = System.nanoTime()
        
        return try {
            val interpreter = this.interpreter
            val inputBuffer = this.inputBuffer
            val outputBuffer = this.outputBuffer
            
            if (interpreter == null || inputBuffer == null || outputBuffer == null) {
                Log.w(TAG, "Inference engine not properly initialized")
                return FrameResult(null, timestampNanos)
            }
            
            // Preprocess the frame
            val preprocessedBitmap = preprocessFrame(frame)
            
            // Convert bitmap to input buffer
            bitmapToBuffer(preprocessedBitmap, inputBuffer)
            
            // Run inference
            inputBuffer.rewind()
            outputBuffer.rewind()
            
            val startTime = System.nanoTime()
            interpreter.run(inputBuffer, outputBuffer)
            val inferenceTime = (System.nanoTime() - startTime) / 1_000_000 // Convert to ms
            
            // Parse output
            val detection = parseOutput(outputBuffer, frame.width, frame.height)
            
            if (inferenceTime > 50) { // Log if inference takes too long
                Log.w(TAG, "Slow inference: ${inferenceTime}ms")
            }
            
            preprocessedBitmap.recycle()
            
            FrameResult(detection, timestampNanos)
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed", e)
            FrameResult(null, timestampNanos)
        }
    }
    
    private fun preprocessFrame(frame: Bitmap): Bitmap {
        // 1. Preprocess with OpenCV-style operations for better detection
        val preprocessed = OpenCVUtils.preprocessForDetection(frame)
        
        // 2. Resize to model input size while maintaining aspect ratio
        val scaledBitmap = Bitmap.createScaledBitmap(preprocessed, INPUT_SIZE, INPUT_SIZE, true)
        preprocessed.recycle()
        
        // 3. Apply Hanning window to reduce edge artifacts
        return applyHanningWindow(scaledBitmap)
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
        
        // Read output: [x, y, w, h, confidence]
        val x = outputBuffer.float
        val y = outputBuffer.float
        val w = outputBuffer.float
        val h = outputBuffer.float
        val confidence = outputBuffer.float
        
        if (confidence < CONFIDENCE_THRESHOLD) {
            return null
        }
        
        // Convert from normalized coordinates to original image coordinates
        val scaleX = originalWidth.toFloat() / INPUT_SIZE
        val scaleY = originalHeight.toFloat() / INPUT_SIZE
        
        val centerX = x * originalWidth
        val centerY = y * originalHeight
        val radius = (max(w, h) * max(originalWidth, originalHeight)) / 2f
        
        return Detection(
            cx = centerX,
            cy = centerY,
            r = radius,
            conf = confidence
        )
    }
    
    override fun close() {
        interpreter?.close()
        gpuDelegate?.close()
        nnApiDelegate?.close()
        
        interpreter = null
        gpuDelegate = null
        nnApiDelegate = null
        inputBuffer = null
        outputBuffer = null
        hanningWindow = null
        
        Log.d(TAG, "Inference engine closed")
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