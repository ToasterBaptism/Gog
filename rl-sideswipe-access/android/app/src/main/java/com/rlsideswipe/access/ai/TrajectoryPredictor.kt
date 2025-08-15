package com.rlsideswipe.access.ai

import kotlin.math.max
import kotlin.math.min

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
        private const val BOUNCE_DAMPING = 0.8f
    }
    
    // Simple state tracking
    private var lastX = 0f
    private var lastY = 0f
    private var velocityX = 0f
    private var velocityY = 0f
    private var lastUpdateMs = 0L
    private var hasValidState = false
    
    override fun update(ball: Detection?, nowMs: Long): List<TrajectoryPoint> {
        if (ball == null) {
            return emptyList()
        }
        
        // Update velocity estimation
        if (hasValidState && lastUpdateMs > 0) {
            val deltaMs = nowMs - lastUpdateMs
            if (deltaMs > 0) {
                velocityX = (ball.cx - lastX) / deltaMs * 1000f // pixels per second
                velocityY = (ball.cy - lastY) / deltaMs * 1000f
            }
        }
        
        lastX = ball.cx
        lastY = ball.cy
        lastUpdateMs = nowMs
        hasValidState = true
        
        // Generate trajectory prediction
        return generateTrajectory(ball.cx, ball.cy, velocityX, velocityY, nowMs)
    }
    
    private fun generateTrajectory(
        startX: Float,
        startY: Float,
        vx: Float,
        vy: Float,
        startTimeMs: Long
    ): List<TrajectoryPoint> {
        val points = mutableListOf<TrajectoryPoint>()
        
        var currentX = startX
        var currentY = startY
        var currentVx = vx
        var currentVy = vy
        var currentTime = startTimeMs
        
        // Simple linear extrapolation with bounds checking
        val steps = (PREDICTION_DURATION_MS / PREDICTION_STEP_MS).toInt()
        
        for (i in 0 until steps) {
            currentTime += PREDICTION_STEP_MS
            
            // Update position
            currentX += currentVx * (PREDICTION_STEP_MS / 1000f)
            currentY += currentVy * (PREDICTION_STEP_MS / 1000f)
            
            // Handle bounces (simplified)
            if (currentX <= 0 || currentX >= ARENA_WIDTH) {
                currentVx = -currentVx * BOUNCE_DAMPING
                currentX = max(0f, min(ARENA_WIDTH, currentX))
            }
            
            if (currentY <= 0 || currentY >= ARENA_HEIGHT) {
                currentVy = -currentVy * BOUNCE_DAMPING
                currentY = max(0f, min(ARENA_HEIGHT, currentY))
            }
            
            points.add(TrajectoryPoint(currentX, currentY, currentTime))
        }
        
        return points
    }
    
    override fun reset() {
        hasValidState = false
        lastUpdateMs = 0L
        velocityX = 0f
        velocityY = 0f
    }
}