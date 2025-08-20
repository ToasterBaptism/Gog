package com.rlsideswipe.access.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

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



/**
 * TensorFlow Lite implementation for ball detection
 * Restored in v2.17 ENHANCED with proper error handling
 */
class TFLiteInferenceEngine(private val context: Context) : InferenceEngine {
    
    companion object {
        private const val TAG = "TFLiteInferenceEngine"
        private const val MODEL_FILE = "rl_sideswipe_ball_v1.tflite"
        private const val INPUT_SIZE = 416 // Model input size
        private const val CONFIDENCE_THRESHOLD = 0.65f
    }
    
    private var interpreter: Interpreter? = null
    private var inputBuffer: ByteBuffer? = null
    private var outputBuffer: Array<Array<FloatArray>>? = null
    
    init {
        try {
            Log.d(TAG, "🤖 Initializing TensorFlow Lite inference engine...")
            loadModel()
            allocateBuffers()
            Log.i(TAG, "✅ TensorFlow Lite inference engine initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize TensorFlow Lite: ${e.message}", e)
            interpreter = null
        }
    }
    
    private fun loadModel() {
        try {
            val modelBuffer = loadModelFile()
            val options = Interpreter.Options().apply {
                setNumThreads(2) // Use 2 threads for better performance
                setUseNNAPI(false) // Disable NNAPI for compatibility
            }
            interpreter = Interpreter(modelBuffer, options)
            Log.d(TAG, "📁 Model loaded: $MODEL_FILE")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to load model: ${e.message}", e)
            throw e
        }
    }
    
    private fun loadModelFile(): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(MODEL_FILE)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
    
    private fun allocateBuffers() {
        // Input buffer: [1, 416, 416, 3] - RGB image
        val inputShape = interpreter?.getInputTensor(0)?.shape()
        Log.d(TAG, "📊 Input shape: ${inputShape?.contentToString()}")
        
        inputBuffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4) // 4 bytes per float
        inputBuffer?.order(ByteOrder.nativeOrder())
        
        // Output buffer: [1, 10647, 85] - detections
        val outputShape = interpreter?.getOutputTensor(0)?.shape()
        Log.d(TAG, "📊 Output shape: ${outputShape?.contentToString()}")
        
        if (outputShape != null && outputShape.size >= 3) {
            outputBuffer = Array(outputShape[0]) { Array(outputShape[1]) { FloatArray(outputShape[2]) } }
        }
    }
    
    override fun warmup() {
        try {
            if (interpreter == null) {
                Log.w(TAG, "⚠️ Cannot warmup - interpreter not initialized")
                return
            }
            
            Log.d(TAG, "🔥 Warming up TensorFlow Lite model...")
            
            // Create dummy input
            inputBuffer?.rewind()
            for (i in 0 until (INPUT_SIZE * INPUT_SIZE * 3)) {
                inputBuffer?.putFloat(0.5f)
            }
            
            // Run inference
            inputBuffer?.rewind()
            interpreter?.run(inputBuffer, outputBuffer)
            
            Log.d(TAG, "✅ TensorFlow Lite warmup completed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Warmup failed: ${e.message}", e)
        }
    }
    
    override fun infer(frame: Bitmap): FrameResult {
        val startTime = System.nanoTime()
        
        try {
            if (interpreter == null || inputBuffer == null || outputBuffer == null) {
                Log.w(TAG, "⚠️ Inference engine not properly initialized")
                return FrameResult(null, startTime)
            }
            
            // Preprocess image
            preprocessImage(frame)
            
            // Run inference
            inputBuffer?.rewind()
            interpreter?.run(inputBuffer, outputBuffer)
            
            // Post-process results
            val detection = postprocessResults()
            
            val inferenceTime = (System.nanoTime() - startTime) / 1_000_000
            if (detection != null) {
                Log.d(TAG, "🎯 Ball detected: (${detection.cx}, ${detection.cy}) conf=${detection.conf} [${inferenceTime}ms]")
            }
            
            return FrameResult(detection, startTime)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Inference failed: ${e.message}", e)
            return FrameResult(null, startTime)
        }
    }
    
    private fun preprocessImage(bitmap: Bitmap) {
        inputBuffer?.rewind()
        
        // Resize bitmap to model input size
        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        
        // Convert to float array and normalize [0, 1]
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        
        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f
            
            inputBuffer?.putFloat(r)
            inputBuffer?.putFloat(g)
            inputBuffer?.putFloat(b)
        }
        
        if (resized != bitmap) {
            resized.recycle()
        }
    }
    
    private fun postprocessResults(): Detection? {
        val output = outputBuffer ?: return null
        
        var bestDetection: Detection? = null
        var bestConfidence = 0f
        
        // Parse YOLO output format
        for (i in output[0].indices) {
            val detection = output[0][i]
            
            if (detection.size >= 85) {
                // YOLO format: [cx, cy, w, h, objectness, class0, class1, ...]
                val objectness = detection[4]
                
                if (objectness > CONFIDENCE_THRESHOLD) {
                    val cx = detection[0] * INPUT_SIZE // Convert to pixel coordinates
                    val cy = detection[1] * INPUT_SIZE
                    val w = detection[2] * INPUT_SIZE
                    val h = detection[3] * INPUT_SIZE
                    val r = maxOf(w, h) / 2f // Use larger dimension as radius
                    
                    // Find best class confidence
                    var maxClassConf = 0f
                    for (j in 5 until detection.size) {
                        if (detection[j] > maxClassConf) {
                            maxClassConf = detection[j]
                        }
                    }
                    
                    val finalConfidence = objectness * maxClassConf
                    
                    if (finalConfidence > bestConfidence) {
                        bestConfidence = finalConfidence
                        bestDetection = Detection(cx, cy, r, finalConfidence)
                    }
                }
            }
        }
        
        return bestDetection
    }
    
    override fun close() {
        try {
            interpreter?.close()
            interpreter = null
            inputBuffer = null
            outputBuffer = null
            Log.d(TAG, "🔒 TensorFlow Lite inference engine closed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error closing inference engine: ${e.message}", e)
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