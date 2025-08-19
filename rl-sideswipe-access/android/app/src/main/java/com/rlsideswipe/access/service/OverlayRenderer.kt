package com.rlsideswipe.access.service

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.rlsideswipe.access.ai.Detection
import com.rlsideswipe.access.ai.TrajectoryPoint

class OverlayRenderer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    companion object {
        private const val BALL_RADIUS_MULTIPLIER = 1.2f
        private const val STROKE_WIDTH = 6f
        private const val OUTLINE_WIDTH = 2f
    }
    
    private var currentBall: Detection? = null
    private var currentTrajectory: List<TrajectoryPoint> = emptyList()
    private var opacity = 0.8f
    
    private val ballPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = STROKE_WIDTH
        isAntiAlias = true
    }
    
    private val ballOutlinePaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = STROKE_WIDTH + OUTLINE_WIDTH
        isAntiAlias = true
    }
    
    private val pathPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }
    
    private val pathOutlinePaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }
    
    fun setDetection(ball: Detection?) {
        currentBall = ball
        invalidate()
    }
    
    fun setTrajectory(trajectory: List<TrajectoryPoint>) {
        currentTrajectory = trajectory
        invalidate()
    }
    
    fun setOpacity(opacity: Float) {
        this.opacity = opacity.coerceIn(0f, 1f)
        updatePaintAlpha()
        invalidate()
    }
    
    private fun updatePaintAlpha() {
        val alpha = (255 * opacity).toInt()
        ballPaint.alpha = alpha
        ballOutlinePaint.alpha = alpha
        pathPaint.alpha = alpha
        pathOutlinePaint.alpha = alpha
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw ball detection
        currentBall?.let { ball ->
            val radius = ball.r * BALL_RADIUS_MULTIPLIER
            
            // Draw outline first
            canvas.drawCircle(ball.cx, ball.cy, radius, ballOutlinePaint)
            // Draw main circle
            canvas.drawCircle(ball.cx, ball.cy, radius, ballPaint)
        }
        
        // Draw trajectory path
        if (currentTrajectory.size > 1) {
            val path = Path()
            val firstPoint = currentTrajectory.first()
            path.moveTo(firstPoint.x, firstPoint.y)
            
            for (i in 1 until currentTrajectory.size) {
                val point = currentTrajectory[i]
                path.lineTo(point.x, point.y)
            }
            
            // Draw outline first
            canvas.drawPath(path, pathOutlinePaint)
            // Draw main path
            canvas.drawPath(path, pathPaint)
            
            // Draw points along the path
            for (point in currentTrajectory) {
                canvas.drawCircle(point.x, point.y, 3f, pathOutlinePaint)
                canvas.drawCircle(point.x, point.y, 3f, pathPaint)
            }
        }
    }
    
    init {
        setBackgroundColor(Color.TRANSPARENT)
        updatePaintAlpha()
    }
}