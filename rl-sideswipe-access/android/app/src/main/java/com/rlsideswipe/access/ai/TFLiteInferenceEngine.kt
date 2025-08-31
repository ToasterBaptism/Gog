package com.rlsideswipe.access.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.rlsideswipe.access.util.OpenCVUtils
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
// NNAPI is built into TensorFlow Lite core, no separate import needed
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.*

/**
 * Production-ready TensorFlow Lite inference engine with comprehensive optimizations
 * Implements 10+ TF Lite best practices for maximum performance and reliability
 */
class TFLiteInferenceEngine(private val context: Context) : InferenceEngine {

    private var interpreter: Interpreter? = null
    private var inputBuffer: ByteBuffer? = null
    private var outputBuffer: ByteBuffer? = null
    private var hanningWindow: FloatArray? = null
    private var gpuDelegate: GpuDelegate? = null
    // NNAPI is handled through interpreter options, no separate delegate needed
    private var isInitialized = false
    private var modelMetadata: ModelMetadata? = null

    // Dynamic model IO metadata used at runtime
    private var inputWidth: Int = 320
    private var inputHeight: Int = 320
    private var inputChannels: Int = 3

    companion object {
        private const val TAG = "TFLiteInferenceEngine"
        private const val MODEL_FILE = "rl_sideswipe_ball_v1.tflite"
        private const val INPUT_SIZE = 320
        private const val CHANNELS = 3
        private const val BYTES_PER_CHANNEL = 4 // Float32
        private const val CONFIDENCE_THRESHOLD = 0.25f
        private const val OUTPUT_SIZE = 5 // [x, y, w, h, confidence]
        private const val MAX_WARMUP_ATTEMPTS = 3
        private const val INFERENCE_TIMEOUT_MS = 100L
    }

    data class ModelMetadata(
        val inputShape: IntArray,
        val outputShape: IntArray,
        val inputDataType: org.tensorflow.lite.DataType,
        val outputDataType: org.tensorflow.lite.DataType,
        val inputQuantized: Boolean,
        val outputQuantized: Boolean,
        val version: String
    )

    init {
        try {
            Log.d(TAG, "🚀 Initializing TensorFlow Lite inference engine...")
            initializeEngine()
            Log.i(TAG, "✅ TensorFlow Lite inference engine initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "🚨 Failed to initialize TensorFlow Lite: ${e.message}", e)
            cleanup()
            throw RuntimeException("TensorFlow Lite initialization failed: ${e.message}", e)
        }
    }

    /**
     * TF Lite Best Practice #1: Comprehensive initialization with error handling
     */
    private fun initializeEngine() {
        loadModel()
        initializeBuffers()
        createHanningWindow()
        validateModel()
        isInitialized = true
    }

    /**
     * TF Lite Best Practice #2: Optimized model loading with hardware acceleration
     */
    private fun loadModel() {
        try {
            Log.d(TAG, "📁 Loading TensorFlow Lite model: $MODEL_FILE")
            val modelBuffer = loadModelFile()
            val options = createOptimizedInterpreterOptions()
            interpreter = Interpreter(modelBuffer, options)
            
            // Extract model metadata
            extractModelMetadata()
            
            Log.i(TAG, "✅ Model loaded successfully with optimizations")
        } catch (e: Exception) {
            Log.e(TAG, "🚨 Failed to load TensorFlow Lite model", e)
            throw e
        }
    }

    /**
     * TF Lite Best Practice #3: Hardware acceleration with fallback strategy
     */
    private fun createOptimizedInterpreterOptions(): Interpreter.Options {
        val options = Interpreter.Options()
        
        // Try GPU acceleration first
        if (isGpuSupported()) {
            try {
                gpuDelegate = GpuDelegate()
                options.addDelegate(gpuDelegate)
                Log.i(TAG, "🎮 GPU acceleration enabled")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ GPU acceleration failed, trying NNAPI: ${e.message}")
                gpuDelegate?.close()
                gpuDelegate = null
            }
        }
        
        // Enable NNAPI if GPU failed (built into TensorFlow Lite)
        if (gpuDelegate == null) {
            try {
                options.setUseNNAPI(true)
                Log.i(TAG, "🧠 NNAPI acceleration enabled")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ NNAPI acceleration failed, using CPU: ${e.message}")
            }
        }
        
        // CPU optimization
        options.setNumThreads(4)
        options.setUseXNNPACK(true)
        
        Log.d(TAG, "🔧 Interpreter options configured")
        return options
    }

    /**
     * TF Lite Best Practice #4: GPU compatibility checking
     */
    private fun isGpuSupported(): Boolean {
        return try {
            val compatibilityList = CompatibilityList()
            val isSupported = compatibilityList.isDelegateSupportedOnThisDevice
            Log.d(TAG, "🎮 GPU support check: $isSupported")
            isSupported
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ GPU compatibility check failed: ${e.message}")
            false
        }
    }

    /**
     * TF Lite Best Practice #5: Model metadata extraction and validation
     */
    private fun extractModelMetadata() {
        val interpreter = this.interpreter ?: return
        
        try {
            val inputTensor = interpreter.getInputTensor(0)
            val outputTensor = interpreter.getOutputTensor(0)

            val inShape = inputTensor.shape()
            val outShape = outputTensor.shape()
            inputHeight = inShape.getOrNull(1) ?: inputHeight
            inputWidth = inShape.getOrNull(2) ?: inputWidth
            inputChannels = inShape.getOrNull(3) ?: inputChannels

            modelMetadata = ModelMetadata(
                inputShape = inShape,
                outputShape = outShape,
                inputDataType = inputTensor.dataType(),
                outputDataType = outputTensor.dataType(),
                inputQuantized = inputTensor.dataType() != org.tensorflow.lite.DataType.FLOAT32,
                outputQuantized = outputTensor.dataType() != org.tensorflow.lite.DataType.FLOAT32,
                version = "v1.0"
            )

            Log.d(TAG, "📊 Model metadata: input=${inShape.contentToString()} ${modelMetadata?.inputDataType}, " +
                      "output=${outShape.contentToString()} ${modelMetadata?.outputDataType}, " +
                      "quantized(in,out)=${modelMetadata?.inputQuantized},${modelMetadata?.outputQuantized}")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Failed to extract model metadata: ${e.message}")
        }
    }

    /**
     * TF Lite Best Practice #6: Model validation
     */
    private fun validateModel() {
        val interpreter = this.interpreter ?: throw IllegalStateException("Interpreter not initialized")
        
        try {
            val inputTensor = interpreter.getInputTensor(0)
            val shape = inputTensor.shape()
            if (shape.size != 4) {
                throw IllegalStateException("Model input shape must be 4D, got ${shape.contentToString()}")
            }
            inputHeight = shape[1]; inputWidth = shape[2]; inputChannels = shape[3]
            Log.d(TAG, "✅ Model validation passed with input=${shape.contentToString()} -> width=$inputWidth height=$inputHeight channels=$inputChannels")
        } catch (e: Exception) {
            Log.e(TAG, "🚨 Model validation failed", e)
            throw e
        }
    }

    /**
     * TF Lite Best Practice #7: Optimized buffer management
     */
    private fun initializeBuffers() {
        try {
            val interpreter = this.interpreter
            if (interpreter != null) {
                val inTensor = interpreter.getInputTensor(0)
                val outTensor = interpreter.getOutputTensor(0)
                val inElems = inTensor.shape().fold(1) { acc, v -> acc * v }
                val outElems = outTensor.shape().fold(1) { acc, v -> acc * v }
                val inBpe = when (inTensor.dataType()) {
                    org.tensorflow.lite.DataType.UINT8, org.tensorflow.lite.DataType.INT8 -> 1
                    else -> 4
                }
                val outBpe = when (outTensor.dataType()) {
                    org.tensorflow.lite.DataType.UINT8, org.tensorflow.lite.DataType.INT8 -> 1
                    else -> 4
                }
                val inputSize = inElems * inBpe
                val outputSize = outElems * outBpe
                inputBuffer = ByteBuffer.allocateDirect(inputSize).apply { order(ByteOrder.nativeOrder()) }
                outputBuffer = ByteBuffer.allocateDirect(outputSize).apply { order(ByteOrder.nativeOrder()) }
                Log.d(TAG, "📦 Buffers initialized (dynamic): input=${inputSize}B (${inTensor.dataType()} ${inTensor.shape().contentToString()}), output=${outputSize}B (${outTensor.dataType()} ${outTensor.shape().contentToString()})")
            } else {
                val inputSize = INPUT_SIZE * INPUT_SIZE * CHANNELS * BYTES_PER_CHANNEL
                inputBuffer = ByteBuffer.allocateDirect(inputSize).apply { order(ByteOrder.nativeOrder()) }
                val outputSize = OUTPUT_SIZE * BYTES_PER_CHANNEL
                outputBuffer = ByteBuffer.allocateDirect(outputSize).apply { order(ByteOrder.nativeOrder()) }
                Log.w(TAG, "📦 Buffers initialized (fallback): input=${inputSize}B, output=${outputSize}B")
            }
        } catch (e: Exception) {
            Log.e(TAG, "🚨 Buffer initialization failed", e)
            throw e
        }
    }

    /**
     * TF Lite Best Practice #8: Advanced preprocessing with Hanning window
     */
    private fun createHanningWindow() {
        try {
            val size = inputWidth
            val center = size / 2f
            val radius = center * 0.9f
            
            hanningWindow = FloatArray(size * size) { i ->
                val x = i % size
                val y = i / size
                val dx = x - center
                val dy = y - center
                val distance = sqrt(dx * dx + dy * dy)
                
                if (distance <= radius) {
                    val normalized = distance / radius
                    (0.5 * (1.0 + cos(PI * normalized))).toFloat()
                } else 0f
            }
            
            Log.d(TAG, "🌊 Hanning window created: ${size}x${size}")
        } catch (e: Exception) {
            Log.e(TAG, "🚨 Hanning window creation failed", e)
            throw e
        }
    }

    private fun loadModelFile(): MappedByteBuffer {
        return try {
            Log.d(TAG, "📁 Loading TensorFlow Lite model: $MODEL_FILE")
            val afd = context.assets.openFd(MODEL_FILE)
            Log.d(TAG, "📁 Model file descriptor: start=${afd.startOffset}, length=${afd.declaredLength}")
            
            FileInputStream(afd.fileDescriptor).channel.use { fc ->
                val buffer = fc.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
                Log.d(TAG, "✅ Model loaded successfully: ${buffer.capacity()} bytes")
                buffer
            }
        } catch (e: Exception) {
            Log.e(TAG, "🚨 Failed to load model file $MODEL_FILE", e)
            throw e
        }
    }

    /**
     * TF Lite Best Practice #9: Proper warmup with multiple iterations
     */
    override fun warmup() {
        if (!isInitialized) {
            Log.w(TAG, "⚠️ Engine not initialized, skipping warmup")
            return
        }
        
        try {
            Log.d(TAG, "🔥 Starting model warmup...")
            val dummy = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
            
            repeat(MAX_WARMUP_ATTEMPTS) { attempt ->
                val start = System.nanoTime()
                infer(dummy)
                val duration = (System.nanoTime() - start) / 1_000_000
                Log.d(TAG, "🔥 Warmup iteration ${attempt + 1}: ${duration}ms")
            }
            
            dummy.recycle()
            Log.i(TAG, "✅ Model warmup completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "🚨 Warmup failed", e)
        }
    }

    /**
     * TF Lite Best Practice #10: Optimized inference with comprehensive monitoring
     */
    override fun infer(frame: Bitmap): FrameResult {
        val ts = System.nanoTime()
        
        if (!isInitialized) {
            Log.e(TAG, "🚨 Engine not initialized")
            return FrameResult(null, ts)
        }
        
        return try {
            val interpreter = this.interpreter ?: run {
                Log.e(TAG, "🚨 Interpreter is null - model not loaded properly")
                return FrameResult(null, ts)
            }
            val input = this.inputBuffer ?: run {
                Log.e(TAG, "🚨 Input buffer is null")
                return FrameResult(null, ts)
            }
            val output = this.outputBuffer ?: run {
                Log.e(TAG, "🚨 Output buffer is null")
                return FrameResult(null, ts)
            }

            Log.d(TAG, "🤖 TFLite inference starting - frame: ${frame.width}x${frame.height}")
            
            // Performance monitoring
            val preprocessStart = System.nanoTime()
            val preprocessed = preprocessFrameAdvanced(frame)
            val preprocessTime = System.nanoTime() - preprocessStart
            Log.d(TAG, "🔄 Advanced preprocessing completed in ${preprocessTime / 1_000_000}ms: ${preprocessed.width}x${preprocessed.height}")
            
            // Convert to input buffer
            val bufferStart = System.nanoTime()
            bitmapToBuffer(preprocessed, input)
            val bufferTime = System.nanoTime() - bufferStart
            Log.d(TAG, "📊 Input buffer prepared in ${bufferTime / 1_000_000}ms")

            // Run inference with performance monitoring
            input.rewind()
            output.rewind()
            
            val inferenceStart = System.nanoTime()
            // Ensure buffer sizes match tensor expectations
            try {
                interpreter.resizeInput(0, intArrayOf(1, inputHeight, inputWidth, inputChannels))
            } catch (_: Exception) {
                // Some interpreters don't require/allow explicit resize when using ByteBuffer
            }
            interpreter.run(input, output)
            val inferenceTime = System.nanoTime() - inferenceStart
            
            Log.d(TAG, "🧠 Model inference completed in ${inferenceTime / 1_000_000}ms")

            // Parse and validate output with timing
            val parseStart = System.nanoTime()
            val detection = parseOutputAdvanced(output, frame.width, frame.height)
            val parseTime = System.nanoTime() - parseStart
            
            // Create performance metrics
            val metrics = TensorFlowLiteUtils.createMetrics(preprocessTime, inferenceTime, parseTime, detection)
            Log.d(TAG, "📊 Performance metrics: total=${metrics.totalTimeMs}ms, conf=${metrics.confidence}")
            
            Log.d(TAG, "📋 Final result: ${detection?.let { "ball at (${it.cx}, ${it.cy}) r=${it.r} conf=${it.conf}" } ?: "no detection"}")
            
            preprocessed.recycle()
            
            // Memory optimization for long-running inference
            if (System.nanoTime() % 100 == 0L) { // Every ~100th frame
                TensorFlowLiteUtils.optimizeMemoryUsage()
            }
            
            FrameResult(detection, ts)
            
        } catch (e: Exception) {
            Log.e(TAG, "🚨 TFLite inference failed: ${e.message}", e)
            FrameResult(null, ts)
        }
    }

    /**
     * Enhanced preprocessing with multiple computer vision techniques
     */
    private fun preprocessFrameAdvanced(frame: Bitmap): Bitmap {
        return try {
            Log.d(TAG, "🔄 Starting advanced preprocessing pipeline...")
            
            // Validate input
            if (!TensorFlowLiteUtils.validateModelInput(frame, frame.width)) {
                Log.w(TAG, "⚠️ Input validation warning, proceeding with preprocessing...")
            }
            
            // Use comprehensive TF Lite preprocessing pipeline
            val targetSize = inputWidth.coerceAtLeast(1)
            val preprocessed = TensorFlowLiteUtils.preprocessForBallDetection(frame, targetSize)
            
            // Apply Hanning window for edge smoothing
            val windowed = applyHanningWindow(preprocessed)
            preprocessed.recycle()
            
            Log.i(TAG, "✅ Advanced preprocessing completed successfully")
            windowed
            
        } catch (e: Exception) {
            Log.e(TAG, "🚨 Advanced preprocessing failed: ${e.message}", e)
            // Fallback to simple scaling
            Bitmap.createScaledBitmap(frame, INPUT_SIZE, INPUT_SIZE, true)
        }
    }

    /**
     * Apply Hanning window for improved edge detection
     */
    private fun applyHanningWindow(bitmap: Bitmap): Bitmap {
        val hw = this.hanningWindow ?: return bitmap
        
        try {
            val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val w = inputWidth
            val h = inputHeight
            val pixels = IntArray(w * h)
            result.getPixels(pixels, 0, w, 0, 0, w, h)
            
            for (i in pixels.indices) {
                val p = pixels[i]
                val a = (p ushr 24) and 0xFF
                val r = (p ushr 16) and 0xFF
                val g = (p ushr 8) and 0xFF
                val b = p and 0xFF
                val w = hw[i]
                
                val nr = (r * w).toInt().coerceIn(0, 255)
                val ng = (g * w).toInt().coerceIn(0, 255)
                val nb = (b * w).toInt().coerceIn(0, 255)
                
                pixels[i] = (a shl 24) or (nr shl 16) or (ng shl 8) or nb
            }
            
            result.setPixels(pixels, 0, w, 0, 0, w, h)
            return result
        } catch (e: Exception) {
            Log.e(TAG, "🚨 Hanning window application failed: ${e.message}", e)
            return bitmap
        }
    }

    /**
     * TF Lite Best Practice #11: Optimized bitmap to buffer conversion
     */
    private fun bitmapToBuffer(bitmap: Bitmap, buffer: ByteBuffer) {
        try {
            buffer.rewind()
            val w = inputWidth
            val h = inputHeight
            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            
            // Write according to input tensor dtype (float/uint8/int8) and channel count
            when (modelMetadata?.inputDataType) {
                org.tensorflow.lite.DataType.UINT8 -> {
                    if (inputChannels == 1) {
                        for (p in pixels) {
                            val r = (p ushr 16) and 0xFF
                            val g = (p ushr 8) and 0xFF
                            val b = p and 0xFF
                            val gray = ((0.299f * r) + (0.587f * g) + (0.114f * b)).toInt().coerceIn(0, 255)
                            buffer.put(gray.toByte())
                        }
                    } else {
                        for (p in pixels) {
                            buffer.put(((p ushr 16) and 0xFF).toByte())
                            buffer.put(((p ushr 8) and 0xFF).toByte())
                            buffer.put((p and 0xFF).toByte())
                        }
                    }
                }
                org.tensorflow.lite.DataType.INT8 -> {
                    if (inputChannels == 1) {
                        for (p in pixels) {
                            val r = (p ushr 16) and 0xFF
                            val g = (p ushr 8) and 0xFF
                            val b = p and 0xFF
                            val gray = ((0.299f * r) + (0.587f * g) + (0.114f * b)).toInt().coerceIn(0, 255) - 128
                            buffer.put(gray.toByte())
                        }
                    } else {
                        for (p in pixels) {
                            buffer.put((((p ushr 16) and 0xFF) - 128).toByte())
                            buffer.put((((p ushr 8) and 0xFF) - 128).toByte())
                            buffer.put(((p and 0xFF) - 128).toByte())
                        }
                    }
                }
                else -> {
                    if (inputChannels == 1) {
                        for (p in pixels) {
                            val r = (p ushr 16) and 0xFF
                            val g = (p ushr 8) and 0xFF
                            val b = p and 0xFF
                            val gray = ((0.299f * r) + (0.587f * g) + (0.114f * b)) / 255.0f
                            buffer.putFloat(gray)
                        }
                    } else {
                        for (p in pixels) {
                            buffer.putFloat(((p ushr 16) and 0xFF) / 255.0f)
                            buffer.putFloat(((p ushr 8) and 0xFF) / 255.0f)
                            buffer.putFloat((p and 0xFF) / 255.0f)
                        }
                    }
                }
            }
            
            Log.d(TAG, "📊 Buffer conversion completed: ${buffer.position()} bytes written")
        } catch (e: Exception) {
            Log.e(TAG, "🚨 Bitmap to buffer conversion failed: ${e.message}", e)
            throw e
        }
    }

    /**
     * TF Lite Best Practice #12: Advanced output parsing with confidence filtering
     */
    private fun parseOutputAdvanced(out: ByteBuffer, origW: Int, origH: Int): Detection? {
        return try {
            out.rewind()
            // Handle possible quantized outputs by inspecting tensor dtype
            var x: Float
            var y: Float
            var w: Float
            var h: Float
            var conf: Float
            run {
                val dtype = modelMetadata?.outputDataType
                when (dtype) {
                    org.tensorflow.lite.DataType.UINT8 -> {
                        x = (out.get().toInt() and 0xFF) / 255f
                        y = (out.get().toInt() and 0xFF) / 255f
                        w = (out.get().toInt() and 0xFF) / 255f
                        h = (out.get().toInt() and 0xFF) / 255f
                        conf = (out.get().toInt() and 0xFF) / 255f
                    }
                    org.tensorflow.lite.DataType.INT8 -> {
                        fun q(): Float = ((out.get().toInt()).coerceIn(-128,127) / 127f).coerceIn(-1f,1f)
                        val qx = q(); val qy = q(); val qw = q(); val qh = q(); val qc = q()
                        x = (qx + 1f) * 0.5f
                        y = (qy + 1f) * 0.5f
                        w = (qw + 1f) * 0.5f
                        h = (qh + 1f) * 0.5f
                        conf = (qc + 1f) * 0.5f
                    }
                    else -> {
                        x = out.float
                        y = out.float
                        w = out.float
                        h = out.float
                        conf = out.float
                    }
                }
            }
            
            Log.d(TAG, "🔍 Raw output: x=$x, y=$y, w=$w, h=$h, conf=$conf")
            
            // Confidence threshold filtering
            if (conf < CONFIDENCE_THRESHOLD) {
                Log.d(TAG, "🚫 Detection below confidence threshold: $conf < $CONFIDENCE_THRESHOLD")
                return null
            }
            
            // Coordinate transformation and validation
            val centerX = (x * origW).coerceIn(0f, origW.toFloat())
            val centerY = (y * origH).coerceIn(0f, origH.toFloat())
            val radius = ((max(w, h) * max(origW, origH)) / 2f).coerceIn(5f, min(origW, origH) / 2f)
            
            // Sanity checks
            if (radius < 5f || centerX < 0 || centerY < 0 || centerX > origW || centerY > origH) {
                Log.w(TAG, "⚠️ Invalid detection coordinates: center=($centerX, $centerY), radius=$radius")
                return null
            }
            
            val detection = Detection(centerX, centerY, radius, conf)
            Log.d(TAG, "✅ Valid detection: $detection")
            detection
            
        } catch (e: Exception) {
            Log.e(TAG, "🚨 Output parsing failed: ${e.message}", e)
            null
        }
    }



    /**
     * TF Lite Best Practice #13: Proper resource cleanup
     */
    private fun cleanup() {
        try {
            interpreter?.close()
            gpuDelegate?.close()
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Cleanup warning: ${e.message}")
        } finally {
            interpreter = null
            gpuDelegate = null
            inputBuffer = null
            outputBuffer = null
            hanningWindow = null
            isInitialized = false
        }
    }

    override fun close() {
        Log.d(TAG, "🔒 Closing TensorFlow Lite inference engine...")
        cleanup()
        Log.d(TAG, "✅ Inference engine closed successfully")
    }

    /**
     * Get current engine status for debugging
     */
    fun getEngineStatus(): Map<String, Any> {
        return mapOf(
            "initialized" to isInitialized,
            "interpreter" to (interpreter != null),
            "gpuAcceleration" to (gpuDelegate != null),
            "nnApiAcceleration" to "built-in",
            "modelMetadata" to (modelMetadata?.toString() ?: "none"),
            "buffers" to mapOf(
                "input" to (inputBuffer != null),
                "output" to (outputBuffer != null),
                "hanningWindow" to (hanningWindow != null)
            )
        )
    }
}