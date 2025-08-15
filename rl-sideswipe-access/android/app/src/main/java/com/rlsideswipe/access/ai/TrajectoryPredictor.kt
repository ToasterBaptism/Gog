package com.rlsideswipe.access.ai

import kotlin.math.*

data class TrajectoryPoint(
    val x: Float,
    val y: Float,
    val tMs: Long
)

interface TrajectoryPredictor {
    fun update(ball: Detection?, nowMs: Long): List<TrajectoryPoint>
    fun reset()
}

class KalmanTrajectoryPredictor : TrajectoryPredictor {
    
    companion object {
        private const val PREDICTION_DURATION_MS = 1200L
        private const val PREDICTION_STEP_MS = 50L
        private const val ARENA_WIDTH = 1080f
        private const val ARENA_HEIGHT = 1920f
        private const val BOUNCE_DAMPING = 0.85f
        private const val GRAVITY = 980f // pixels/s^2 (approximate for game physics)
        private const val MIN_VELOCITY_THRESHOLD = 10f // pixels/s
    }
    
    // Kalman filter state: [x, y, vx, vy, ax, ay]
    private val state = FloatArray(6) // position, velocity, acceleration
    private val covariance = Array(6) { FloatArray(6) } // error covariance matrix
    private val processNoise = Array(6) { FloatArray(6) } // process noise
    private val measurementNoise = Array(2) { FloatArray(2) } // measurement noise (x, y only)
    
    private var lastUpdateMs = 0L
    private var hasValidState = false
    private var consecutiveMisses = 0
    
    init {
        initializeKalmanFilter()
    }
    
    private fun initializeKalmanFilter() {
        // Initialize process noise matrix
        for (i in 0..5) {
            for (j in 0..5) {
                processNoise[i][j] = if (i == j) {
                    when (i) {
                        0, 1 -> 1f // position noise
                        2, 3 -> 10f // velocity noise
                        4, 5 -> 100f // acceleration noise
                        else -> 0f
                    }
                } else 0f
            }
        }
        
        // Initialize measurement noise matrix (position measurements only)
        measurementNoise[0][0] = 25f // x measurement noise
        measurementNoise[1][1] = 25f // y measurement noise
        
        // Initialize covariance matrix with high uncertainty
        for (i in 0..5) {
            for (j in 0..5) {
                covariance[i][j] = if (i == j) 1000f else 0f
            }
        }
    }
    
    override fun update(ball: Detection?, nowMs: Long): List<TrajectoryPoint> {
        if (ball == null) {
            consecutiveMisses++
            if (consecutiveMisses > 5) {
                reset()
            }
            return if (hasValidState) {
                generateTrajectoryFromState(nowMs)
            } else {
                emptyList()
            }
        }
        
        consecutiveMisses = 0
        val deltaMs = if (lastUpdateMs > 0) nowMs - lastUpdateMs else 50L
        val dt = deltaMs / 1000f // Convert to seconds
        
        if (!hasValidState) {
            // Initialize state with first detection
            state[0] = ball.cx // x
            state[1] = ball.cy // y
            state[2] = 0f // vx
            state[3] = 0f // vy
            state[4] = 0f // ax
            state[5] = GRAVITY // ay (gravity)
            hasValidState = true
        } else {
            // Kalman filter predict step
            predict(dt)
            
            // Kalman filter update step
            update(ball.cx, ball.cy)
        }
        
        lastUpdateMs = nowMs
        
        // Generate trajectory prediction
        return generateTrajectoryFromState(nowMs)
    }
    
    private fun predict(dt: Float) {
        // State transition: x = x + vx*dt + 0.5*ax*dt^2
        val newState = FloatArray(6)
        newState[0] = state[0] + state[2] * dt + 0.5f * state[4] * dt * dt // x
        newState[1] = state[1] + state[3] * dt + 0.5f * state[5] * dt * dt // y
        newState[2] = state[2] + state[4] * dt // vx
        newState[3] = state[3] + state[5] * dt // vy
        newState[4] = state[4] // ax (assume constant)
        newState[5] = state[5] // ay (assume constant)
        
        // Copy back to state
        for (i in 0..5) {
            state[i] = newState[i]
        }
        
        // Update covariance matrix (simplified)
        for (i in 0..5) {
            covariance[i][i] += processNoise[i][i] * dt
        }
    }
    
    private fun update(measuredX: Float, measuredY: Float) {
        // Kalman gain calculation (simplified for position measurements)
        val kx = covariance[0][0] / (covariance[0][0] + measurementNoise[0][0])
        val ky = covariance[1][1] / (covariance[1][1] + measurementNoise[1][1])
        
        // Update state with measurements
        state[0] += kx * (measuredX - state[0])
        state[1] += ky * (measuredY - state[1])
        
        // Update covariance (simplified)
        covariance[0][0] *= (1f - kx)
        covariance[1][1] *= (1f - ky)
    }
    
    private fun generateTrajectoryFromState(startTimeMs: Long): List<TrajectoryPoint> {
        val points = mutableListOf<TrajectoryPoint>()
        
        var currentX = state[0]
        var currentY = state[1]
        var currentVx = state[2]
        var currentVy = state[3]
        var currentAx = state[4]
        var currentAy = state[5]
        var currentTime = startTimeMs
        
        val dt = PREDICTION_STEP_MS / 1000f
        val steps = (PREDICTION_DURATION_MS / PREDICTION_STEP_MS).toInt()
        
        for (i in 0 until steps) {
            currentTime += PREDICTION_STEP_MS
            
            // Physics integration with acceleration
            currentX += currentVx * dt + 0.5f * currentAx * dt * dt
            currentY += currentVy * dt + 0.5f * currentAy * dt * dt
            currentVx += currentAx * dt
            currentVy += currentAy * dt
            
            // Handle wall bounces with proper physics
            var bounced = false
            
            if (currentX <= 0) {
                currentX = 0f
                currentVx = -currentVx * BOUNCE_DAMPING
                bounced = true
            } else if (currentX >= ARENA_WIDTH) {
                currentX = ARENA_WIDTH
                currentVx = -currentVx * BOUNCE_DAMPING
                bounced = true
            }
            
            if (currentY <= 0) {
                currentY = 0f
                currentVy = -currentVy * BOUNCE_DAMPING
                bounced = true
            } else if (currentY >= ARENA_HEIGHT) {
                currentY = ARENA_HEIGHT
                currentVy = -currentVy * BOUNCE_DAMPING
                bounced = true
            }
            
            // Apply air resistance (simplified)
            if (!bounced) {
                currentVx *= 0.999f
                currentVy *= 0.999f
            }
            
            // Stop predicting if velocity is too low
            val speed = sqrt(currentVx * currentVx + currentVy * currentVy)
            if (speed < MIN_VELOCITY_THRESHOLD) {
                break
            }
            
            points.add(TrajectoryPoint(currentX, currentY, currentTime))
        }
        
        return points
    }
    
    override fun reset() {
        hasValidState = false
        lastUpdateMs = 0L
        consecutiveMisses = 0
        
        // Reset state vector
        for (i in 0..5) {
            state[i] = 0f
        }
        
        // Reset covariance matrix with high uncertainty
        for (i in 0..5) {
            for (j in 0..5) {
                covariance[i][j] = if (i == j) 1000f else 0f
            }
        }
    }
}