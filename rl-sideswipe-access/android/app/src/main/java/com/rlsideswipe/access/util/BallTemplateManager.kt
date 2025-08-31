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
        private const val SIMILARITY_THRESHOLD = 0.15f // EXTREMELY low threshold for debugging
        private const val FALSE_POSITIVE_THRESHOLD = 1.1f // Allow perfect matches (disabled)
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
                            // Ensure template is 80x80 for better detection of larger balls
                            val resizedBitmap = if (bitmap.width != 80 || bitmap.height != 80) {
                                Log.w(TAG, "⚠️ Resizing template $filename from ${bitmap.width}x${bitmap.height} to 80x80")
                                val scaled = Bitmap.createScaledBitmap(bitmap, 80, 80, true)
                                if (scaled != bitmap) {
                                    bitmap.recycle() // Recycle original if a new bitmap was created
                                }
                                scaled
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
     * Create synthetic templates as fallback
     */
    private fun createSyntheticTemplate() {
        // Create normal synthetic template
        syntheticTemplate = createEnhancedSyntheticBall()
        if (syntheticTemplate != null) {
            ballTemplates.add(BallTemplate("synthetic_fallback", syntheticTemplate!!, 
                TemplateMetadata("normal", "unknown", "synthetic", 0.7f)))
        }
        
        // Create very dark synthetic template for cases like user's screenshot
        val darkTemplate = createVeryDarkSyntheticBall()
        if (darkTemplate != null) {
            ballTemplates.add(BallTemplate("synthetic_very_dark", darkTemplate, 
                TemplateMetadata("dark", "unknown", "synthetic", 0.8f)))
        }
        
        // Create large gray template matching user's screenshot
        val largeGrayTemplate = createLargeGraySyntheticBall()
        if (largeGrayTemplate != null) {
            ballTemplates.add(BallTemplate("synthetic_large_gray", largeGrayTemplate, 
                TemplateMetadata("normal", "unknown", "synthetic", 0.9f)))
        }
    }
    
    /**
     * Create realistic RL Sideswipe ball template based on actual game images
     * Features: metallic gray base, hexagonal grid pattern, white circular elements
     */
    private fun createEnhancedSyntheticBall(): Bitmap {
        val size = 80
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
     * Create very dark synthetic ball template for dark lighting conditions
     * Based on user's screenshot showing much darker ball
     */
    private fun createVeryDarkSyntheticBall(): Bitmap {
        val size = 80
        val template = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val centerX = size / 2f
        val centerY = size / 2f
        val radius = size / 2.1f
        
        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = x - centerX
                val dy = y - centerY
                val distance = sqrt(dx * dx + dy * dy)
                
                if (distance <= radius) {
                    val normalizedX = (x - centerX) / radius
                    val normalizedY = (y - centerY) / radius
                    
                    // Much darker base color (like in user's screenshot)
                    var baseIntensity = 55 // Very dark base
                    
                    // Hexagonal grid pattern
                    val gridSize = 8f
                    val gridX = (normalizedX * gridSize).toInt()
                    val gridY = (normalizedY * gridSize).toInt()
                    
                    val isGridLine = (gridX + gridY) % 2 == 0 || 
                                   abs(normalizedX * gridSize - gridX.toFloat()) < 0.15f ||
                                   abs(normalizedY * gridSize - gridY.toFloat()) < 0.15f
                    
                    if (isGridLine) {
                        baseIntensity -= 10 // Even darker grid lines
                    }
                    
                    // Subtle white spots (much dimmer than normal)
                    val spotDistance1 = sqrt((normalizedX - 0.3f) * (normalizedX - 0.3f) + (normalizedY - 0.2f) * (normalizedY - 0.2f))
                    val spotDistance2 = sqrt((normalizedX + 0.2f) * (normalizedX + 0.2f) + (normalizedY + 0.4f) * (normalizedY + 0.4f))
                    
                    if (spotDistance1 < 0.15f || spotDistance2 < 0.12f) {
                        baseIntensity += 25 // Subtle bright spots
                    }
                    
                    // 3D shading effect (very subtle)
                    val lightAngle = atan2(normalizedY, normalizedX)
                    val lightFactor = cos(lightAngle - PI/4) * 0.2f + 0.8f
                    val shadedIntensity = (baseIntensity * lightFactor).toInt()
                    
                    // Dark color with slight blue-gray tint
                    val red = (shadedIntensity * 0.85f).toInt().coerceIn(0, 255)
                    val green = (shadedIntensity * 0.88f).toInt().coerceIn(0, 255)
                    val blue = (shadedIntensity * 0.95f).toInt().coerceIn(0, 255)
                    
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
     * Create large gray synthetic ball template matching user's screenshot
     * Features: larger size, prominent hexagonal pattern, gray metallic appearance
     */
    private fun createLargeGraySyntheticBall(): Bitmap {
        val size = 80
        val template = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val centerX = size / 2f
        val centerY = size / 2f
        val radius = size / 2.1f
        
        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = x - centerX
                val dy = y - centerY
                val distance = sqrt(dx * dx + dy * dy)
                
                if (distance <= radius) {
                    val normalizedX = (x - centerX) / radius
                    val normalizedY = (y - centerY) / radius
                    
                    // Medium gray base color (like in user's screenshot)
                    var baseIntensity = 120 // Medium gray
                    
                    // Prominent hexagonal grid pattern (larger scale for 80x80)
                    val gridSize = 12f // Larger grid for bigger template
                    val gridX = (normalizedX * gridSize).toInt()
                    val gridY = (normalizedY * gridSize).toInt()
                    
                    // Create more prominent hexagonal grid lines
                    val isGridLine = (gridX + gridY) % 2 == 0 || 
                                   abs(normalizedX * gridSize - gridX.toFloat()) < 0.2f ||
                                   abs(normalizedY * gridSize - gridY.toFloat()) < 0.2f
                    
                    if (isGridLine) {
                        baseIntensity -= 25 // More prominent darker grid lines
                    }
                    
                    // Add hexagonal cell centers (brighter spots)
                    val cellCenterX = (normalizedX * gridSize + 0.5f).toInt() - 0.5f
                    val cellCenterY = (normalizedY * gridSize + 0.5f).toInt() - 0.5f
                    val cellDistance = sqrt((normalizedX * gridSize - cellCenterX) * (normalizedX * gridSize - cellCenterX) + 
                                          (normalizedY * gridSize - cellCenterY) * (normalizedY * gridSize - cellCenterY))
                    
                    if (cellDistance < 0.3f) {
                        baseIntensity += 15 // Brighter cell centers
                    }
                    
                    // Prominent white/bright spots (like in user's screenshot)
                    val spotDistance1 = sqrt((normalizedX - 0.25f) * (normalizedX - 0.25f) + (normalizedY - 0.15f) * (normalizedY - 0.15f))
                    val spotDistance2 = sqrt((normalizedX + 0.3f) * (normalizedX + 0.3f) + (normalizedY + 0.2f) * (normalizedY + 0.2f))
                    val spotDistance3 = sqrt((normalizedX - 0.1f) * (normalizedX - 0.1f) + (normalizedY + 0.35f) * (normalizedY + 0.35f))
                    
                    if (spotDistance1 < 0.12f || spotDistance2 < 0.1f || spotDistance3 < 0.08f) {
                        baseIntensity += 40 // Bright reflective spots
                    }
                    
                    // 3D shading effect
                    val lightAngle = atan2(normalizedY, normalizedX)
                    val lightFactor = cos(lightAngle - PI/4) * 0.25f + 0.75f
                    val shadedIntensity = (baseIntensity * lightFactor).toInt()
                    
                    // Gray metallic color with slight blue tint
                    val red = (shadedIntensity * 0.90f).toInt().coerceIn(0, 255)
                    val green = (shadedIntensity * 0.93f).toInt().coerceIn(0, 255)
                    val blue = (shadedIntensity * 1.02f).toInt().coerceIn(0, 255)
                    
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
            Log.d(TAG, "🔍 Testing template: ${template.name}")
            val matches = detectWithSingleTemplate(bitmap, template, startX, endX, startY, endY)
            Log.d(TAG, "🎯 Template ${template.name}: ${matches.size} matches")
            matches.forEach { match ->
                Log.d(TAG, "  📍 Match: (${match.x.toInt()}, ${match.y.toInt()}) similarity=${String.format("%.3f", match.similarity)}")
            }
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
        
        // Multi-scale matching with wider range for different ball sizes
        val scales = listOf(0.6f, 0.8f, 1.0f, 1.2f, 1.4f, 1.6f)
        
        for (scale in scales) {
            val scaledWidth = (templateBitmap.width * scale).toInt()
            val scaledHeight = (templateBitmap.height * scale).toInt()
            
            if (scaledWidth < 20 || scaledHeight < 20) continue
            if (scaledWidth > 120 || scaledHeight > 120) continue
            
            val scaledTemplate = Bitmap.createScaledBitmap(templateBitmap, scaledWidth, scaledHeight, true)
            
            // Search with adaptive step size
            val searchStep = maxOf(4, scaledWidth / 10)
            var bestSimilarity = 0f
            var bestX = 0
            var bestY = 0
            var totalChecks = 0
            
            for (y in startY until (endY - scaledHeight) step searchStep) {
                for (x in startX until (endX - scaledWidth) step searchStep) {
                    val similarity = calculateEnhancedSimilarity(bitmap, scaledTemplate, x, y)
                    totalChecks++
                    
                    if (similarity > bestSimilarity) {
                        bestSimilarity = similarity
                        bestX = x
                        bestY = y
                    }
                    
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
            
            Log.d(TAG, "🔍 Template ${template.name} scale ${scale}: checked $totalChecks positions")
            Log.d(TAG, "🔍   Best similarity = ${String.format("%.3f", bestSimilarity)} at ($bestX, $bestY)")
            Log.d(TAG, "🔍   Threshold = ${SIMILARITY_THRESHOLD}, False positive = ${FALSE_POSITIVE_THRESHOLD}")
            Log.d(TAG, "🔍   Matches found this scale: ${matches.size}")
            
            // Log some sample similarity values for debugging
            if (bestSimilarity > 0.05f) {
                Log.d(TAG, "🔍   POTENTIAL DETECTION: Best similarity ${String.format("%.3f", bestSimilarity)} ${if (bestSimilarity >= SIMILARITY_THRESHOLD) "PASSES" else "FAILS"} threshold")
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
                        
                        // Enhanced boost for RL ball characteristics including all ball types
                        val colorBonus = if (isMetallicGrayColor(bRed, bGreen, bBlue)) {
                            val avgBrightness = (bRed + bGreen + bBlue) / 3
                            // Extra boost for white spots (reflective elements)
                            if (bRed > 180 && bGreen > 180 && bBlue > 180) 0.35f
                            // Strong boost for very dark balls
                            else if (avgBrightness in 25..80) 0.40f
                            // Strong boost for medium gray balls (like in user's new screenshot)
                            else if (avgBrightness in 100..160) 0.45f
                            // Boost for other metallic gray variations
                            else if (avgBrightness in 80..200) 0.30f
                            // Standard boost for other ball colors
                            else 0.25f
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
            
            // DEBUGGING: Rely much more on texture/shape, less on color
            return (avgColorSimilarity * 0.20f + avgTextureSimilarity * 0.80f).coerceIn(0f, 1f)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating similarity", e)
            return 0f
        }
    }
    
    /**
     * Check if color matches RL Sideswipe ball characteristics
     * Based on analysis of 10 real ball images + support for very dark balls
     */
    private fun isMetallicGrayColor(red: Int, green: Int, blue: Int): Boolean {
        val avgColor = (red + green + blue) / 3
        
        // EXTREMELY PERMISSIVE for debugging - accept almost any color
        val isInBallRange = avgColor in 10..250 // Accept almost any brightness
        
        // Very permissive color balance check
        val colorVariance = maxOf(abs(red - green), abs(green - blue), abs(red - blue))
        val isGrayish = colorVariance < 80 // Very permissive variance
        
        // Accept any reasonable color combination
        val hasAnyTint = true // Accept any color for debugging
        
        // Accept any bright colors
        val isAnyBright = avgColor > 120
        
        // Accept any dark colors  
        val isAnyDark = avgColor < 150
        
        // Accept any medium colors
        val isAnyMedium = avgColor in 50..200
        
        // DEBUGGING: Accept almost everything to see what we're missing
        return isInBallRange || isAnyBright || isAnyDark || isAnyMedium
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
     * Add a learned template from manual ball positioning
     */
    fun addLearnedTemplate(bitmap: Bitmap) {
        try {
            // Create a copy to avoid external modifications (force ARGB_8888 to avoid unsupported configs)
            val templateCopy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
            
            // Generate unique name for learned template
            val learnedCount = ballTemplates.count { it.name.startsWith("learned_") }
            val templateName = "learned_${learnedCount + 1}_${System.currentTimeMillis()}"
            
            // Create metadata for learned template
            val metadata = TemplateMetadata(
                lightingCondition = "learned",
                ballState = "manual_positioned",
                mapType = "user_game",
                confidence = 1.2f // Higher confidence for user-provided templates
            )
            
            // Add to templates list
            val learnedTemplate = BallTemplate(templateName, templateCopy, metadata)
            ballTemplates.add(learnedTemplate)
            
            Log.d(TAG, "📸 Added learned template: $templateName (${templateCopy.width}x${templateCopy.height})")
            Log.d(TAG, "📸 Total templates now: ${ballTemplates.size}")
            
            // Log template statistics
            val learnedTemplatesCount = ballTemplates.count { it.name.startsWith("learned_") }
            Log.d(TAG, "📸 Learned templates: $learnedTemplatesCount, Built-in templates: ${ballTemplates.size - learnedTemplatesCount}")
            
        } catch (e: Exception) {
            Log.e(TAG, "📸 Error adding learned template", e)
        }
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        ballTemplates.forEach { it.bitmap.recycle() }
        ballTemplates.clear()
        // syntheticTemplate is already recycled in the forEach loop above, so don't recycle again
        syntheticTemplate = null
    }
}