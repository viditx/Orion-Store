package com.orion.store.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

val PillShape = RoundedCornerShape(50)

val ColorScheme.isLightTheme: Boolean
    get() = surface.luminance() > 0.5f

/**
 * Pure Compose scalloped / polygon expressive shapes matching AndroidX MaterialShapes
 * (Cookie9Sided, Cookie12Sided, Cookie6Sided, Sunny, Clover4Leaf, Gem, Flower, Pentagon).
 */
object OrionShapes {
    private fun scallopedPolygon(lobes: Int, valleyDepth: Float, sharpness: Float = 1.0f): Shape = GenericShape { size, _ ->
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val maxR = minOf(w, h) / 2f
        val steps = lobes * 16
        val path = Path()
        for (i in 0..steps) {
            val theta = (i.toFloat() / steps) * (2.0 * PI) - PI / 2.0
            val wave = cos(lobes * theta)
            val normWave = ((wave + 1.0) / 2.0)
            val curved = Math.pow(normWave, sharpness.toDouble()).toFloat()
            val r = maxR * (1f - valleyDepth + valleyDepth * curved)
            val x = cx + (r * cos(theta)).toFloat()
            val y = cy + (r * sin(theta)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        addPath(path)
    }

    private fun roundedPolygon(sides: Int, cornerRoundness: Float = 0.16f): Shape = GenericShape { size, _ ->
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val maxR = minOf(w, h) / 2f
        val path = Path()
        val pts = (0 until sides).map { i ->
            val angle = (2.0 * PI * i / sides) - PI / 2.0
            (cx + maxR * cos(angle).toFloat()) to (cy + maxR * sin(angle).toFloat())
        }
        for (i in 0 until sides) {
            val prev = pts[(i - 1 + sides) % sides]
            val curr = pts[i]
            val next = pts[(i + 1) % sides]
            val p1x = curr.first + (prev.first - curr.first) * cornerRoundness
            val p1y = curr.second + (prev.second - curr.second) * cornerRoundness
            val p2x = curr.first + (next.first - curr.first) * cornerRoundness
            val p2y = curr.second + (next.second - curr.second) * cornerRoundness
            if (i == 0) path.moveTo(p1x, p1y) else path.lineTo(p1x, p1y)
            path.quadraticTo(curr.first, curr.second, p2x, p2y)
        }
        path.close()
        addPath(path)
    }

    val Cookie12Sided: Shape = scallopedPolygon(12, 0.10f, 0.85f)
    val Cookie9Sided: Shape = scallopedPolygon(9, 0.13f, 0.85f)
    val Cookie7Sided: Shape = scallopedPolygon(7, 0.15f, 0.85f)
    val Cookie6Sided: Shape = scallopedPolygon(6, 0.17f, 0.85f)
    val Sunny: Shape = scallopedPolygon(8, 0.22f, 1.4f)
    val Clover4Leaf: Shape = scallopedPolygon(4, 0.28f, 0.72f)
    val Flower: Shape = scallopedPolygon(8, 0.24f, 0.68f)
    val SoftBurst: Shape = scallopedPolygon(10, 0.19f, 1.3f)
    val Pentagon: Shape = roundedPolygon(5, 0.20f)
    val Gem: Shape = roundedPolygon(6, 0.16f)

    val BLOBS = listOf(Cookie9Sided, Sunny, Clover4Leaf, Cookie6Sided, Pentagon, Flower, Gem)
    val RANK_SHAPES = listOf(Cookie9Sided, Cookie7Sided, Cookie6Sided, Sunny, Clover4Leaf, Pentagon)
}

/** Pastel container + legible foreground tone matching Common.kt tonal(). */
@Composable
fun tonalPair(c: Color): Pair<Color, Color> {
    val hsl = FloatArray(3)
    val argb = c.toArgb()
    java.awt.Color.RGBtoHSB((argb ushr 16) and 0xFF, (argb ushr 8) and 0xFF, argb and 0xFF, hsl)
    val hue = hsl[0] * 360f
    val sat = hsl[1]
    val light = MaterialTheme.colorScheme.isLightTheme
    return Color.hsl(hue, (sat * 0.70f).coerceAtMost(0.75f), if (light) 0.88f else 0.26f) to
        Color.hsl(hue, sat.coerceAtMost(0.80f), if (light) 0.30f else 0.82f)
}

fun parseHexColor(hex: String?, fallback: Color): Color {
    val s = hex?.trim()?.removePrefix("#").orEmpty()
    if (s.length != 6 && s.length != 8) return fallback
    return runCatching {
        val v = s.toLong(16)
        if (s.length == 6) Color(0xFF000000L or v) else Color(v)
    }.getOrDefault(fallback)
}

fun compactCount(n: Int): String = compactCount(n.toLong())
fun compactCount(n: Long): String = when {
    n < 1000 -> "$n"
    n < 1_000_000 -> "%.1fk".format(Locale.US, n / 1000.0).replace(".0k", "k")
    else -> "%.1fM".format(Locale.US, n / 1_000_000.0).replace(".0M", "M")
}

fun categoryIcon(cat: String): ImageVector = when (cat.lowercase()) {
    "music", "audio" -> Icons.Default.MusicNote
    "media", "movies", "video", "streaming" -> Icons.Default.PlayCircle
    "browsers", "browser" -> Icons.Default.Language
    "privacy", "security" -> Icons.Default.Shield
    "development", "developer" -> Icons.Default.Code
    "customization" -> Icons.Default.Palette
    "games", "gaming" -> Icons.Default.SportsEsports
    "social" -> Icons.Default.Forum
    "reading" -> Icons.AutoMirrored.Filled.MenuBook
    "productivity" -> Icons.Default.Work
    "health" -> Icons.Default.Favorite
    "anime" -> Icons.Default.AutoAwesome
    else -> Icons.Default.Build
}

fun faIcon(name: String?, fallback: ImageVector = Icons.Default.Layers): ImageVector = when (name?.lowercase()) {
    "fa-headphones", "fa-music" -> Icons.Default.Headphones
    "fa-film", "fa-photo-film", "fa-video" -> Icons.Default.Movie
    "fa-dragon" -> Icons.Default.AutoAwesome
    "fa-heart-pulse", "fa-heart" -> Icons.Default.Favorite
    "fa-user-shield", "fa-shield" -> Icons.Default.Shield
    "fa-comments" -> Icons.Default.Forum
    "fa-code", "fa-terminal" -> Icons.Default.Code
    "fa-book-open" -> Icons.AutoMirrored.Filled.MenuBook
    "fa-briefcase" -> Icons.Default.Work
    "fa-toolbox" -> Icons.Default.Build
    "fa-bolt" -> Icons.Default.Bolt
    "fa-wand-magic-sparkles" -> Icons.Default.AutoAwesome
    "fa-rocket" -> Icons.Default.RocketLaunch
    else -> fallback
}

val LocalSmoothMotion = staticCompositionLocalOf { true }

@Composable
fun Modifier.expressiveSpringClick(
    shape: Shape = RoundedCornerShape(20.dp),
    onClick: () -> Unit
): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val hovered by src.collectIsHoveredAsState()
    val smooth = LocalSmoothMotion.current
    val scale by animateFloatAsState(
        targetValue = if (!smooth) 1f else when {
            pressed -> 0.975f
            hovered -> 1.018f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.70f, stiffness = Spring.StiffnessMediumLow)
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(shape)
        .hoverable(src)
        .clickable(interactionSource = src, indication = null, onClick = onClick)
}

/**
 * Gentle hover/press scale without clipping or click handling.
 * Calibrated with Emil Kowalski spring micro-physics and smoothMotion awareness.
 */
@Composable
fun Modifier.hoverScale(
    hoverTarget: Float = 1.025f,
    pressedTarget: Float = 0.975f
): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val hovered by src.collectIsHoveredAsState()
    val smooth = LocalSmoothMotion.current
    val scale by animateFloatAsState(
        targetValue = if (!smooth) 1f else when {
            pressed -> pressedTarget
            hovered -> hoverTarget
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.70f, stiffness = Spring.StiffnessMediumLow)
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .hoverable(src)
}

@Composable
fun SpinnerShape(
    shape: Shape,
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    periodMs: Int = 24_000,
    animate: Boolean = true
) {
    val turn by rememberInfiniteTransition(label = "spin").animateFloat(
        initialValue = 0f,
        targetValue = if (animate) 360f else 0f,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing)),
        label = "deg"
    )
    Box(modifier.size(size).graphicsLayer { rotationZ = turn }.background(color, shape))
}

@Composable
fun TonalIconCircle(icon: ImageVector, accent: Color) {
    val (bg, fg) = tonalPair(accent)
    Box(Modifier.size(40.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(22.dp), tint = fg)
    }
}

private val VIVID_SEEDS = listOf(
    Color(0xFFEF4444), // Red
    Color(0xFF3B82F6), // Cobalt
    Color(0xFF10B981), // Emerald
    Color(0xFFA855F7), // Purple
    Color(0xFFF97316), // Orange
    Color(0xFF06B6D4), // Cyan
    Color(0xFFEC4899), // Pink
    Color(0xFF6366F1), // Indigo
    Color(0xFF14B8A6), // Teal
    Color(0xFFEAB308)  // Amber
)

fun appSeedAccent(key: String): Color = VIVID_SEEDS[abs(key.hashCode()) % VIVID_SEEDS.size]

/**
 * Slow, drifting "aurora" wash built from the active palette (primary / tertiary / secondary).
 * Sits behind content at low alpha so surfaces stay readable while the whole app feels alive.
 * Colours come straight from the repo's Material 3 schemes via the active ColorScheme.
 */
@Composable
fun ExpressiveBackdrop(
    modifier: Modifier = Modifier,
    alpha: Float = 1f
) {
    val cs = MaterialTheme.colorScheme
    val turn by rememberInfiniteTransition(label = "aurora").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(26_000, easing = LinearEasing)),
        label = "deg"
    )
    modifier.drawBehind {
        val w = size.width
        val h = size.height
        val r = maxOf(w, h) * 0.58f
        val rad = Math.toRadians(turn.toDouble())
        fun glow(c: Color, cx: Float, cy: Float, strength: Float) {
            val center = Offset(cx, cy)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(c.copy(alpha = strength * alpha), Color.Transparent),
                    center = center,
                    radius = r
                ),
                radius = r,
                center = center
            )
        }
        glow(cs.primary, w * (0.24f + 0.10f * cos(rad).toFloat()), h * 0.10f, 0.14f)
        glow(cs.tertiary, w * (0.82f + 0.08f * sin(rad).toFloat()), h * 0.18f, 0.11f)
        glow(cs.secondary, w * 0.5f, h * (0.92f + 0.05f * cos(rad).toFloat()), 0.10f)
    }
}
