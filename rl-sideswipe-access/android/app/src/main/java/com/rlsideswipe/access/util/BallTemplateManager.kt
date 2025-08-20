package com.rlsideswipe.access.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.IOException
import kotlin.math.*

/**
 * Multi-template ball detection system for Rocket League Sideswipe
 * Manages multiple real ball templates and provides ensemble matching
 */
class BallTemplateManager(private val context: Context) {
    
    companion object {
        private const val TAG = "BallTemplateManager"
        private const val TEMPLATE_FOLDER = "ball_templates"
        private const val SIMILARITY_THRESHOLD = 0.85f // Higher threshold for better selectivity
        private const val FALSE_POSITIVE_THRESHOLD = 0.99f // Perfect matches are suspicious
        private const val MAX_HORIZONTAL_MATCHES = 10 // Max matches in same Y band
        private const val REGULAR_SPACING_THRESHOLD = 6 // UI elements have regular spacing
    }
    
    private val ballTemplates = mutableListOf<BallTemplate>()
    private var syntheticTemplate: Bitmap? = null
    
    data class BallTemplate(
        val name: String,
        val bitmap: Bitmap,
        val metadata: TemplateMetadata = TemplateMetadata()
    )
    
    data class TemplateMetadata(
        val lightingCondition: String = "unknown", // bright, dark, normal
        val ballState: String = "unknown", // moving, stationary, rotating
        val mapType: String = "unknown", // arena type if known
        val confidence: Float = 1.0f // Template reliability
    )
    
    data class TemplateMatch(
        val x: Float,
        val y: Float,
        val similarity: Float,
        val templateName: String,
        val radius: Float = 30f
    )
    
    /**
     * Initialize the template manager and load all available templates
     */
    fun initialize() {
        Log.d(TAG, "🎯 Initializing multi-template ball detection system...")
        
        // Load real ball templates from assets
        loadRealBallTemplates()
        
        // Create synthetic template as fallback
        createSyntheticTemplate()
        
        Log.d(TAG, "✅ Template system initialized: ${ballTemplates.size} real templates + 1 synthetic")
    }
    
    /**
     * Load real 60x60 ball images from assets folder
     */
    private fun loadRealBallTemplates() {
        try {
            val assetManager = context.assets
            val templateFiles = assetManager.list(TEMPLATE_FOLDER) ?: emptyArray()
            
            Log.d(TAG, "📁 Found ${templateFiles.size} template files in assets/$TEMPLATE_FOLDER")
            
            for (filename in templateFiles) {
                if (filename.endsWith(".png") || filename.endsWith(".jpg")) {
                    try {
                        val inputStream = assetManager.open("$TEMPLATE_FOLDER/$filename")
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        inputStream.close()
                        
                        if (bitmap != null) {
                            // Ensure template is 60x60 as expected
                            val resizedBitmap = if (bitmap.width != 60 || bitmap.height != 60) {
                                Log.w(TAG, "⚠️ Resizing template $filename from ${bitmap.width}x${bitmap.height} to 60x60")
                                Bitmap.createScaledBitmap(bitmap, 60, 60, true)
                            } else {
                                bitmap
                            }
                            
                            // Extract metadata from filename if possible
                            val metadata = parseTemplateMetadata(filename)
                            
                            ballTemplates.add(BallTemplate(filename, resizedBitmap, metadata))
                            Log.d(TAG, "✅ Loaded template: $filename (${metadata.lightingCondition})")
                        } else {
                            Log.e(TAG, "❌ Failed to decode template: $filename")
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "❌ Error loading template $filename", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error accessing template assets", e)
        }
    }
    
    /**
     * Parse metadata from template filename
     * Expected format: ball_bright_moving.png, ball_dark_stationary.png, etc.
     */
    private fun parseTemplateMetadata(filename: String): TemplateMetadata {
        val parts = filename.lowercase().replace(".png", "").replace(".jpg", "").split("_")
        
        var lightingCondition = "normal"
        var ballState = "unknown"
        var confidence = 1.0f
        
        for (part in parts) {
            when (part) {
                "bright", "light" -> lightingCondition = "bright"
                "dark", "shadow" -> lightingCondition = "dark"
                "moving", "motion" -> ballState = "moving"
                "stationary", "still" -> ballState = "stationary"
                "rotating", "spin" -> ballState = "rotating"
            }
        }
        
        return TemplateMetadata(lightingCondition, ballState, "unknown", confidence)
    }
    
    /**
     * Create synthetic template as fallback
     */
    private fun createSyntheticTemplate() {
        syntheticTemplate = createEnhancedSyntheticBall()
        if (syntheticTemplate != null) {
            ballTemplates.add(BallTemplate("synthetic_fallback", syntheticTemplate!!, 
                TemplateMetadata("normal", "unknown", "synthetic", 0.7f)))
        }
    }
    
    /**
     * Create realistic RL Sideswipe ball template based on actual game images
     * Features: metallic gray base, hexagonal grid pattern, white circular elements
     */
    private fun createEnhancedSyntheticBall(): Bitmap {
        val size = 60
        val template = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val centerX = size / 2f
        val centerY = size / 2f
        val radius = size / 2.1f // Slightly larger for better coverage
        
        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = x - centerX
                val dy = y - centerY
                val distance = sqrt(dx * dx + dy * dy)
                
                if (distance <= radius) {
                    val normalizedX = (x - centerX) / radius
                    val normalizedY = (y - centerY) / radius
                    
                    // Base metallic gray color (observed from real images)
                    var baseIntensity = 135
                    
                    // Hexagonal grid pattern (characteristic of RL ball)
                    val gridSize = 8f
                    val gridX = (normalizedX * gridSize).toInt()
                    val gridY = (normalizedY * gridSize).toInt()
                    
                    // Create hexagonal grid lines
                    val isGridLine = (gridX + gridY) % 2 == 0 || 
                                   abs(normalizedX * gridSize - gridX.toFloat()) < 0.15f ||
                                   abs(normalizedY * gridSize - gridY.toFloat()) < 0.15f
                    
                    if (isGridLine) {
                        baseIntensity -= 15 // Darker grid lines
                    }
                    
                    // Add white circular elements (lights/reflective spots)
                    val spotDistance1 = sqrt((normalizedX - 0.3f).pow(2) + (normalizedY - 0.2f).pow(2))
                    val spotDistance2 = sqrt((normalizedX + 0.4f).pow(2) + (normalizedY - 0.3f).pow(2))
                    val spotDistance3 = sqrt((normalizedX - 0.1f).pow(2) + (normalizedY + 0.4f).pow(2))
                    
                    if (spotDistance1 < 0.12f || spotDistance2 < 0.1f || spotDistance3 < 0.08f) {
                        baseIntensity = 240 // Bright white spots
                    }
                    
                    // 3D shading effect
                    val lightAngle = atan2(normalizedY, normalizedX)
                    val lightFactor = cos(lightAngle - PI/4) * 0.3f + 0.7f
                    val shadedIntensity = (baseIntensity * lightFactor).toInt()
                    
                    // Metallic color with slight blue-gray tint
                    val red = (shadedIntensity * 0.88f).toInt().coerceIn(0, 255)
                    val green = (shadedIntensity * 0.92f).toInt().coerceIn(0, 255)
                    val blue = (shadedIntensity * 1.05f).toInt().coerceIn(0, 255)
                    
                    val color = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
                    template.setPixel(x, y, color)
                } else {
                    template.setPixel(x, y, 0x00000000) // Transparent
                }
            }
        }
        
        return template
    }
    
    /**
     * Detect balls using ensemble template matching with false positive filtering
     */
    fun detectBalls(bitmap: Bitmap, startX: Int, endX: Int, startY: Int, endY: Int): List<TemplateMatch> {
        if (ballTemplates.isEmpty()) {
            Log.w(TAG, "⚠️ No templates loaded, cannot detect balls")
            return emptyList()
        }
        
        val allMatches = mutableListOf<TemplateMatch>()
        
        // Try each template
        for (template in ballTemplates) {
            val matches = detectWithSingleTemplate(bitmap, template, startX, endX, startY, endY)
            allMatches.addAll(matches)
        }
        
        Log.d(TAG, "🔍 Raw template matches: ${allMatches.size}")
        
        // Apply false positive filtering
        val filteredMatches = applyFalsePositiveFiltering(allMatches)
        
        Log.d(TAG, "✅ After filtering: ${filteredMatches.size} matches")
        
        return filteredMatches
    }
    
    /**
     * Detect balls using a single template
     */
    private fun detectWithSingleTemplate(
        bitmap: Bitmap, 
        template: BallTemplate, 
        startX: Int, 
        endX: Int, 
        startY: Int, 
        endY: Int
    ): List<TemplateMatch> {
        val matches = mutableListOf<TemplateMatch>()
        val templateBitmap = template.bitmap
        
        // Multi-scale matching
        val scales = listOf(0.8f, 1.0f, 1.2f, 1.4f)
        
        for (scale in scales) {
            val scaledWidth = (templateBitmap.width * scale).toInt()
            val scaledHeight = (templateBitmap.height * scale).toInt()
            
            if (scaledWidth < 20 || scaledHeight < 20) continue
            if (scaledWidth > 120 || scaledHeight > 120) continue
            
            val scaledTemplate = Bitmap.createScaledBitmap(templateBitmap, scaledWidth, scaledHeight, true)
            
            // Search with adaptive step size
            val searchStep = maxOf(4, scaledWidth / 10)
            
            for (y in startY until (endY - scaledHeight) step searchStep) {
                for (x in startX until (endX - scaledWidth) step searchStep) {
                    val similarity = calculateEnhancedSimilarity(bitmap, scaledTemplate, x, y)
                    
                    if (similarity >= SIMILARITY_THRESHOLD && similarity < FALSE_POSITIVE_THRESHOLD) {
                        matches.add(TemplateMatch(
                            x = x + scaledWidth / 2f,
                            y = y + scaledHeight / 2f,
                            similarity = similarity,
                            templateName = template.name,
                            radius = scaledWidth / 2f
                        ))
                    }
                }
            }
            
            scaledTemplate.recycle()
        }
        
        return matches
    }
    
    /**
     * Apply smart false positive filtering
     */
    private fun applyFalsePositiveFiltering(matches: List<TemplateMatch>): List<TemplateMatch> {
        if (matches.isEmpty()) return matches
        
        var filteredMatches = matches.toMutableList()
        val originalCount = filteredMatches.size
        
        // 1. Remove perfect matches (likely UI elements)
        filteredMatches = filteredMatches.filter { it.similarity < FALSE_POSITIVE_THRESHOLD }.toMutableList()
        val afterPerfectFilter = filteredMatches.size
        
        // 2. Detect and remove horizontal line patterns (UI elements)
        filteredMatches = filterHorizontalLinePatterns(filteredMatches)
        val afterHorizontalFilter = filteredMatches.size
        
        // 3. Detect and remove regular spacing patterns
        filteredMatches = filterRegularSpacingPatterns(filteredMatches)
        val afterSpacingFilter = filteredMatches.size
        
        // 4. Remove clustered duplicates (keep best match in each cluster)
        filteredMatches = removeDuplicateClusters(filteredMatches)
        val finalCount = filteredMatches.size
        
        Log.d(TAG, "🚫 False positive filtering: $originalCount → $afterPerfectFilter → $afterHorizontalFilter → $afterSpacingFilter → $finalCount matches")
        
        return filteredMatches
    }
    
    /**
     * Filter out horizontal line patterns (UI elements)
     */
    private fun filterHorizontalLinePatterns(matches: List<TemplateMatch>): MutableList<TemplateMatch> {
        val filtered = mutableListOf<TemplateMatch>()
        val yGroups = matches.groupBy { (it.y / 10).toInt() } // Group by Y bands
        
        for ((yBand, groupMatches) in yGroups) {
            if (groupMatches.size > MAX_HORIZONTAL_MATCHES) {
                Log.d(TAG, "🚫 Filtering out ${groupMatches.size} matches at Y≈${yBand * 10} (horizontal line pattern)")
            } else {
                filtered.addAll(groupMatches)
            }
        }
        
        return filtered
    }
    
    /**
     * Filter out regular spacing patterns (UI elements)
     */
    private fun filterRegularSpacingPatterns(matches: List<TemplateMatch>): MutableList<TemplateMatch> {
        if (matches.size < 3) return matches.toMutableList()
        
        val filtered = mutableListOf<TemplateMatch>()
        val sortedMatches = matches.sortedBy { it.x }
        
        var i = 0
        while (i < sortedMatches.size) {
            val current = sortedMatches[i]
            
            // Look for regular spacing pattern
            var regularSpacingCount = 1
            var lastX = current.x
            
            for (j in i + 1 until sortedMatches.size) {
                val next = sortedMatches[j]
                val spacing = next.x - lastX
                
                if (abs(spacing - REGULAR_SPACING_THRESHOLD) < 2f) {
                    regularSpacingCount++
                    lastX = next.x
                } else {
                    break
                }
            }
            
            if (regularSpacingCount >= 5) {
                Log.d(TAG, "🚫 Filtering out $regularSpacingCount matches with regular spacing (UI pattern)")
                i += regularSpacingCount
            } else {
                filtered.add(current)
                i++
            }
        }
        
        return filtered
    }
    
    /**
     * Remove duplicate matches in the same area (keep best)
     */
    private fun removeDuplicateClusters(matches: List<TemplateMatch>): MutableList<TemplateMatch> {
        if (matches.isEmpty()) return mutableListOf()
        
        val filtered = mutableListOf<TemplateMatch>()
        val processed = mutableSetOf<Int>()
        
        for (i in matches.indices) {
            if (i in processed) continue
            
            val current = matches[i]
            val cluster = mutableListOf(current)
            processed.add(i)
            
            // Find nearby matches
            for (j in i + 1 until matches.size) {
                if (j in processed) continue
                
                val other = matches[j]
                val distance = sqrt((current.x - other.x).pow(2) + (current.y - other.y).pow(2))
                
                if (distance < 40f) { // Within 40 pixels
                    cluster.add(other)
                    processed.add(j)
                }
            }
            
            // Keep the best match from the cluster
            val bestMatch = cluster.maxByOrNull { it.similarity }
            if (bestMatch != null) {
                filtered.add(bestMatch)
            }
        }
        
        return filtered
    }
    
    /**
     * Enhanced similarity calculation with color and texture analysis
     */
    private fun calculateEnhancedSimilarity(bitmap: Bitmap, template: Bitmap, startX: Int, startY: Int): Float {
        try {
            val templateWidth = template.width
            val templateHeight = template.height
            
            var colorSimilarity = 0f
            var textureSimilarity = 0f
            var pixelCount = 0
            
            for (ty in 0 until templateHeight) {
                for (tx in 0 until templateWidth) {
                    val bx = startX + tx
                    val by = startY + ty
                    
                    if (bx >= 0 && bx < bitmap.width && by >= 0 && by < bitmap.height) {
                        val templatePixel = template.getPixel(tx, ty)
                        val bitmapPixel = bitmap.getPixel(bx, by)
                        
                        // Skip transparent template pixels
                        if ((templatePixel ushr 24) < 128) continue
                        
                        // Color similarity with RL ball color boost
                        val tRed = (templatePixel shr 16) and 0xFF
                        val tGreen = (templatePixel shr 8) and 0xFF
                        val tBlue = templatePixel and 0xFF
                        
                        val bRed = (bitmapPixel shr 16) and 0xFF
                        val bGreen = (bitmapPixel shr 8) and 0xFF
                        val bBlue = bitmapPixel and 0xFF
                        
                        // Enhanced boost for RL ball characteristics
                        val colorBonus = if (isMetallicGrayColor(bRed, bGreen, bBlue)) {
                            // Extra boost for white spots (reflective elements)
                            if (bRed > 180 && bGreen > 180 && bBlue > 180) 0.3f
                            // Boost for metallic gray base
                            else if ((bRed + bGreen + bBlue) / 3 in 100..160) 0.25f
                            // Standard boost for other ball colors
                            else 0.15f
                        } else 0f
                        
                        val colorDiff = sqrt(
                            ((tRed - bRed) * (tRed - bRed) +
                             (tGreen - bGreen) * (tGreen - bGreen) +
                             (tBlue - bBlue) * (tBlue - bBlue)).toFloat()
                        ) / 441.67f // Normalize to 0-1
                        
                        colorSimilarity += (1f - colorDiff + colorBonus)
                        
                        // Texture similarity
                        val templateBrightness = getPixelBrightness(templatePixel)
                        val bitmapBrightness = getPixelBrightness(bitmapPixel)
                        val brightnessDiff = abs(templateBrightness - bitmapBrightness) / 255f
                        
                        textureSimilarity += (1f - brightnessDiff)
                        pixelCount++
                    }
                }
            }
            
            if (pixelCount == 0) return 0f
            
            val avgColorSimilarity = colorSimilarity / pixelCount
            val avgTextureSimilarity = textureSimilarity / pixelCount
            
            // Weighted combination: color is more important for RL ball
            return (avgColorSimilarity * 0.7f + avgTextureSimilarity * 0.3f).coerceIn(0f, 1f)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating similarity", e)
            return 0f
        }
    }
    
    /**
     * Check if color matches RL Sideswipe ball characteristics
     * Based on analysis of 10 real ball images
     */
    private fun isMetallicGrayColor(red: Int, green: Int, blue: Int): Boolean {
        val avgColor = (red + green + blue) / 3
        
        // Primary check: Metallic gray range (observed: 95-175)
        val isInGrayRange = avgColor in 85..185
        
        // Color balance check: RL ball has slight blue-gray tint
        val colorVariance = maxOf(abs(red - green), abs(green - blue), abs(red - blue))
        val isGrayish = colorVariance < 35 // Tighter variance for specificity
        
        // Blue-gray tint characteristic of RL ball
        val hasBlueGrayTint = blue >= red - 5 && blue >= green - 5 && blue <= red + 25 && blue <= green + 25
        
        // White spot detection (bright reflective elements)
        val isWhiteSpot = avgColor > 180 && colorVariance < 25 && 
                         red > 160 && green > 160 && blue > 160
        
        // Dark grid line detection (hexagonal pattern)
        val isDarkGrid = avgColor in 60..120 && colorVariance < 30 &&
                        red in 50..130 && green in 50..130 && blue in 50..140
        
        // Metallic highlight detection (3D shading)
        val isMetallicHighlight = avgColor in 140..200 && colorVariance < 40 &&
                                 hasBlueGrayTint
        
        return (isInGrayRange && isGrayish && hasBlueGrayTint) || 
               isWhiteSpot || isDarkGrid || isMetallicHighlight
    }
    
    /**
     * Get pixel brightness
     */
    private fun getPixelBrightness(pixel: Int): Int {
        val red = (pixel shr 16) and 0xFF
        val green = (pixel shr 8) and 0xFF
        val blue = pixel and 0xFF
        return (red + green + blue) / 3
    }
    
    /**
     * Get template count for debugging
     */
    fun getTemplateCount(): Int = ballTemplates.size
    
    /**
     * Get template names for debugging
     */
    fun getTemplateNames(): List<String> = ballTemplates.map { it.name }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        ballTemplates.forEach { it.bitmap.recycle() }
        ballTemplates.clear()
        syntheticTemplate?.recycle()
        syntheticTemplate = null
    }
}