package com.rlsideswipe.access.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.rlsideswipe.access.util.OpenCVUtils
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sqrt

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
        private const val CONFIDENCE_THRESHOLD = 0.25f
        private const val OUTPUT_SIZE = 5 // [x, y, w, h, confidence]
    }

    init {
        try {
            Log.d(TAG, "Initializing TensorFlow Lite inference engine...")
            loadModel()
            initializeBuffers()
            createHanningWindow()
            Log.i(TAG, "TensorFlow Lite inference engine initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TensorFlow Lite: ${e.message}", e)
            cleanup()
            throw RuntimeException("TensorFlow Lite initialization failed: ${e.message}", e)
        }
    }

    private fun cleanup() {
        try { interpreter?.close() } catch (_: Exception) {}
        interpreter = null
        inputBuffer = null
        outputBuffer = null
        hanningWindow = null
    }

    private fun loadModel() {
        try {
            val modelBuffer = loadModelFile()
            val options = Interpreter.Options().apply {
                // Prefer CPU with 4 threads; add delegates later if needed
                setNumThreads(4)
            }
            interpreter = Interpreter(modelBuffer, options)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load TensorFlow Lite model", e)
            throw e
        }
    }

    private fun initializeBuffers() {
        val inputSize = INPUT_SIZE * INPUT_SIZE * CHANNELS * BYTES_PER_CHANNEL
        inputBuffer = ByteBuffer.allocateDirect(inputSize).apply { order(ByteOrder.nativeOrder()) }

        val outputSize = OUTPUT_SIZE * BYTES_PER_CHANNEL
        outputBuffer = ByteBuffer.allocateDirect(outputSize).apply { order(ByteOrder.nativeOrder()) }
    }

    private fun createHanningWindow() {
        val size = INPUT_SIZE
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
    }

    private fun loadModelFile(): MappedByteBuffer {
        try {
            Log.d(TAG, "📁 Loading TensorFlow Lite model: $MODEL_FILE")
            val afd = context.assets.openFd(MODEL_FILE)
            Log.d(TAG, "📁 Model file descriptor: start=${afd.startOffset}, length=${afd.declaredLength}")
            
            FileInputStream(afd.fileDescriptor).channel.use { fc ->
                val buffer = fc.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
                Log.d(TAG, "✅ Model loaded successfully: ${buffer.capacity()} bytes")
                return buffer
            }
        } catch (e: Exception) {
            Log.e(TAG, "🚨 Failed to load model file $MODEL_FILE", e)
            throw e
        }
    }

    override fun warmup() {
        try {
            val dummy = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
            repeat(2) { infer(dummy) }
            dummy.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "Warmup failed", e)
        }
    }

    override fun infer(frame: Bitmap): FrameResult {
        val ts = System.nanoTime()
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
            
            val preprocessed = preprocessFrame(frame)
            Log.d(TAG, "🔄 Preprocessed frame: ${preprocessed.width}x${preprocessed.height}")
            
            bitmapToBuffer(preprocessed, input)
            Log.d(TAG, "📊 Input buffer prepared")

            input.rewind(); output.rewind()
            interpreter.run(input, output)
            Log.d(TAG, "🧠 Model inference completed")

            val det = parseOutput(output, frame.width, frame.height)
            Log.d(TAG, "📋 Parsed output: ${det?.let { "ball at (${it.cx}, ${it.cy}) conf=${it.conf}" } ?: "no detection"}")
            
            preprocessed.recycle()
            FrameResult(det, ts)
        } catch (e: Exception) {
            Log.e(TAG, "🚨 TFLite inference failed: ${e.message}", e)
            FrameResult(null, ts)
        }
    }

    private fun preprocessFrame(frame: Bitmap): Bitmap {
        val pre = OpenCVUtils.adaptivePreprocessForDetection(frame)
        val scaled = Bitmap.createScaledBitmap(pre, INPUT_SIZE, INPUT_SIZE, true)
        pre.recycle()
        return applyHanningWindow(scaled)
    }

    private fun applyHanningWindow(bitmap: Bitmap): Bitmap {
        val hw = this.hanningWindow ?: return bitmap
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        result.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
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
        result.setPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        return result
    }

    private fun bitmapToBuffer(bitmap: Bitmap, buffer: ByteBuffer) {
        buffer.rewind()
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (p in pixels) {
            buffer.putFloat(((p ushr 16) and 0xFF) / 255.0f)
            buffer.putFloat(((p ushr 8) and 0xFF) / 255.0f)
            buffer.putFloat((p and 0xFF) / 255.0f)
        }
    }

    private fun parseOutput(out: ByteBuffer, origW: Int, origH: Int): Detection? {
        out.rewind()
        val x = out.float
        val y = out.float
        val w = out.float
        val h = out.float
        val conf = out.float
        if (conf < CONFIDENCE_THRESHOLD) return null
        val centerX = x * origW
        val centerY = y * origH
        val radius = (max(w, h) * max(origW, origH)) / 2f
        return Detection(centerX, centerY, radius, conf)
    }

    override fun close() {
        try { interpreter?.close() } catch (_: Exception) {}
        interpreter = null
        inputBuffer = null
        outputBuffer = null
        hanningWindow = null
        Log.d(TAG, "Inference engine closed")
    }
}
