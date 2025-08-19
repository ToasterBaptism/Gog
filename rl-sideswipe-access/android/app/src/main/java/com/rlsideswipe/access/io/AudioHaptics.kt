package com.rlsideswipe.access.io

import android.content.Context
import android.media.SoundPool
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.rlsideswipe.access.R

class AudioHaptics(private val context: Context) {
    
    companion object {
        private const val TAG = "AudioHaptics"
        private const val MAX_STREAMS = 4
        private const val HAPTIC_DURATION_MS = 100L
        private const val APPROACH_INTERVAL_MS = 600L
    }
    
    private var soundPool: SoundPool? = null
    private var vibrator: Vibrator? = null
    
    private var approachSoundId = 0
    private var bounceSoundId = 0
    private var soundsLoaded = false
    
    private var lastApproachTime = 0L
    private var audioEnabled = true
    private var hapticsEnabled = true
    
    init {
        initializeAudio()
        initializeHaptics()
    }
    
    private fun initializeAudio() {
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            soundPool = SoundPool.Builder()
                .setMaxStreams(MAX_STREAMS)
                .setAudioAttributes(attrs)
                .build()
            
            soundPool?.let { pool ->
                pool.setOnLoadCompleteListener { _, sampleId, status ->
                    if (status == 0) {
                        if (sampleId == approachSoundId || sampleId == bounceSoundId) {
                            if (approachSoundId != 0 && bounceSoundId != 0) {
                                soundsLoaded = true
                            }
                        }
                    }
                }
                approachSoundId = pool.load(context, R.raw.approach, 1)
                bounceSoundId = pool.load(context, R.raw.bounce, 1)
            }
            
            Log.d(TAG, "Audio initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize audio", e)
        }
    }
    
    private fun initializeHaptics() {
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            
            Log.d(TAG, "Haptics initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize haptics", e)
        }
    }
    
    fun playApproachSound() {
        if (!audioEnabled) return
        
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastApproachTime < APPROACH_INTERVAL_MS) {
            return
        }
        
        lastApproachTime = currentTime
        
        try {
            if (soundsLoaded && approachSoundId != 0) {
                soundPool?.play(approachSoundId, 1.0f, 1.0f, 1, 0, 1.0f)
            } else {
                Log.w(TAG, "Approach sound not yet loaded")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play approach sound", e)
        }
    }
    
    fun playBounceSound() {
        if (!audioEnabled) return
        
        try {
            if (soundsLoaded && bounceSoundId != 0) {
                soundPool?.play(bounceSoundId, 1.0f, 1.0f, 1, 0, 1.0f)
            } else {
                Log.w(TAG, "Bounce sound not yet loaded")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play bounce sound", e)
        }
    }
    
    fun triggerHapticFeedback() {
        if (!hapticsEnabled) return
        
        try {
            vibrator?.let { vib ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createOneShot(HAPTIC_DURATION_MS, VibrationEffect.DEFAULT_AMPLITUDE)
                    vib.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vib.vibrate(HAPTIC_DURATION_MS)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger haptic feedback", e)
        }
    }
    
    fun setAudioEnabled(enabled: Boolean) {
        audioEnabled = enabled
    }
    
    fun setHapticsEnabled(enabled: Boolean) {
        hapticsEnabled = enabled
    }
    
    fun release() {
        try {
            soundPool?.release()
            soundPool = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing audio resources", e)
        }
    }
}