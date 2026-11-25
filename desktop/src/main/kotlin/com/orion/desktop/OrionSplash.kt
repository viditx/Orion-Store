package com.orion.desktop

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Orion's splash mark, drawn from the Android vector itself.
 *
 * [SPLASH_BLOB_POINTS] is the `blob` path of `app/src/main/res/drawable/ic_splash.xml`, flattened to
 * a polygon: 216 points on the 100 × 100 vector viewport. Using the real outline instead of an
 * approximation means the desktop intro is the same cookie the Android app opens with.
 */
private const val SPLASH_BLOB_POINTS =
    "50.00,17.60 50.94,17.70 51.87,17.98 52.76,18.42 53.62,19.01 54.44,19.70 55.21,20.46 55.94,21.23 56.64,21.98 57.33,22.66 58.01,23.25 58.71,23.72 59.44,24.06 60.22,24.27 61.06,24.36 61.96,24.35 62.93,24.26 63.94,24.14 65.00,24.02 66.08,23.94 67.14,23.93 68.18,24.04 69.16,24.27 70.04,24.65 70.83,25.18 71.49,25.86 72.01,26.67 72.41,27.59 72.69,28.59 72.87,29.64 72.98,30.72 73.05,31.78 73.10,32.80 73.18,33.77 73.33,34.66 73.56,35.47 73.90,36.20 74.37,36.86 74.95,37.47 75.65,38.04 76.44,38.59 77.30,39.15 78.19,39.74 79.07,40.37 79.89,41.05 80.62,41.80 81.21,42.60 81.65,43.46 81.91,44.37 81.98,45.32 81.86,46.28 81.58,47.24 81.15,48.19 80.61,49.11 80.00,50.00 79.37,50.85 78.75,51.67 78.20,52.47 77.73,53.24 77.39,54.01 77.18,54.79 77.11,55.60 77.17,56.44 77.34,57.33 77.59,58.26 77.89,59.24 78.19,60.26 78.46,61.31 78.65,62.36 78.73,63.40 78.67,64.40 78.45,65.34 78.06,66.20 77.51,66.97 76.80,67.63 75.96,68.18 75.03,68.63 74.02,68.99 72.98,69.28 71.95,69.53 70.95,69.76 70.01,70.01 69.16,70.31 68.40,70.68 67.74,71.14 67.17,71.71 66.67,72.40 66.23,73.18 65.83,74.06 65.42,75.00 65.00,75.98 64.53,76.95 64.00,77.88 63.40,78.73 62.71,79.45 61.93,80.03 61.08,80.45 60.17,80.68 59.20,80.73 58.20,80.62 57.20,80.36 56.19,79.99 55.21,79.54 54.26,79.07 53.34,78.61 52.47,78.20 51.62,77.87 50.81,77.67 50.00,77.60 49.19,77.67 48.38,77.87 47.53,78.20 46.66,78.61 45.74,79.07 44.79,79.54 43.81,79.99 42.80,80.36 41.80,80.62 40.80,80.73 39.83,80.68 38.92,80.45 38.07,80.03 37.29,79.45 36.60,78.73 36.00,77.88 35.47,76.95 35.00,75.98 34.58,75.00 34.17,74.06 33.77,73.18 33.33,72.40 32.83,71.71 32.26,71.14 31.60,70.68 30.84,70.31 29.99,70.01 29.05,69.76 28.05,69.53 27.02,69.28 25.98,68.99 24.97,68.63 24.04,68.18 23.20,67.63 22.49,66.97 21.94,66.20 21.55,65.34 21.33,64.40 21.27,63.40 21.35,62.36 21.54,61.31 21.81,60.26 22.11,59.24 22.41,58.26 22.66,57.33 22.83,56.44 22.89,55.60 22.82,54.79 22.61,54.01 22.27,53.24 21.80,52.47 21.25,51.67 20.63,50.85 20.00,50.00 19.39,49.11 18.85,48.19 18.42,47.24 18.14,46.28 18.02,45.32 18.09,44.37 18.35,43.46 18.79,42.60 19.38,41.80 20.11,41.05 20.93,40.37 21.81,39.74 22.70,39.15 23.56,38.59 24.35,38.04 25.05,37.47 25.63,36.86 26.10,36.20 26.44,35.47 26.67,34.66 26.82,33.77 26.90,32.80 26.95,31.78 27.02,30.72 27.13,29.64 27.31,28.59 27.59,27.59 27.99,26.67 28.51,25.86 29.17,25.18 29.96,24.65 30.84,24.27 31.82,24.04 32.86,23.93 33.92,23.94 35.00,24.02 36.06,24.14 37.07,24.26 38.04,24.35 38.94,24.36 39.78,24.27 40.56,24.06 41.29,23.72 41.99,23.25 42.67,22.66 43.36,21.98 44.06,21.23 44.79,20.46 45.56,19.70 46.38,19.01 47.24,18.42 48.13,17.98 49.06,17.70"

/**
 * Android draws the splash mark at a fixed 108dp, which fills a phone but reads as a speck in a
 * desktop window. The desktop keeps Android's *proportions* instead of its absolute size: the
 * cookie ends up a little over a fifth of the window's width, close to the share it takes on a
 * phone, and shrinks to fit short windows.
 */
private const val SplashMarkWidthFraction = 0.22f
private const val SplashMarkHeightFraction = 0.52f
private val SplashMarkMin = 160.dp
private val SplashMarkMax = 520.dp

private fun blobPath(unit: Float, ox: Float, oy: Float): Path {
    val path = Path()
    var first = true
    for (token in SPLASH_BLOB_POINTS.split(' ')) {
        val comma = token.indexOf(',')
        val x = token.substring(0, comma).toFloat() * unit + ox
        val y = token.substring(comma + 1).toFloat() * unit + oy
        if (first) {
            path.moveTo(x, y)
            first = false
        } else {
            path.lineTo(x, y)
        }
    }
    path.close()
    return path
}

/**
 * The animated Orion splash: the cookie springs open while turning, then the triangle, circle and
 * square pop in, and the little "RE" pill is the last thing to land — the same order and timing as
 * `ic_splash_animated.xml`.
 *
 * @param markColor the cookie colour (Android's `splash_shape`).
 * @param cutoutColor the colour of the glyphs knocked out of it (Android's `splash_logo`).
 */
@Composable
fun OrionAnimatedSplashMark(
    progress: Float,
    markColor: Color,
    cutoutColor: Color,
    modifier: Modifier = Modifier
) {
    fun stage(start: Float, end: Float): Float = ((progress - start) / (end - start)).coerceIn(0f, 1f)
    // Android's overshoot interpolator (tension 2) — the same curve the vector animator drives.
    fun overshoot(value: Float): Float {
        val t = value - 1f
        val output = t * t * (3f * t + 2f) + 1f
        return if (3f * t + 2f > 0f) output else output * output
    }
    val blob = overshoot(stage(0f, 0.55f)).coerceIn(0f, 1.12f)
    val triangle = overshoot(stage(0.30f, 0.65f)).coerceIn(0f, 1.12f)
    val circle = overshoot(stage(0.42f, 0.77f)).coerceIn(0f, 1.12f)
    val square = overshoot(stage(0.54f, 0.90f)).coerceIn(0f, 1.12f)
    val re = overshoot(stage(0.72f, 1f)).coerceIn(0f, 1.12f)
    val blobRotation = -120f + 120f * FastOutSlowInEasing.transform(stage(0f, 0.90f))
    // The wordmark settles in once the mark has landed, so the screen is never a lone icon.
    val wordAlpha = ((progress - 0.62f) / 0.38f).coerceIn(0f, 1f)
    // The halo breathes while the splash waits for the store, so a slow start still feels alive.
    val pulse by rememberInfiniteTransition(label = "halo").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse),
        label = "halo-pulse"
    )

    BoxWithConstraints(modifier) {
        val markSize = minOf(maxWidth * SplashMarkWidthFraction, maxHeight * SplashMarkHeightFraction)
            .coerceIn(SplashMarkMin, SplashMarkMax)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
    Canvas(Modifier.size(markSize)) {
        // The cookie's own bounding box inside the vector's 100x100 viewport (18.02..81.98 across,
        // 17.6..80.73 down). Drawing into that box rather than the whole viewport keeps the mark
        // tight in its canvas, so the wordmark can sit right under the cookie instead of a band
        // below it.
        val unit = size.width / 63.96f
        val ox = -18.02f * unit
        val oy = -17.60f * unit
        val center = Offset(50f * unit + ox, 49.165f * unit + oy)

        // A soft brand halo, so the mark sits on the background instead of floating on it
        val glow = 42f * unit
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(markColor.copy(alpha = (0.14f + 0.10f * pulse) * blob), Color.Transparent),
                center = center,
                radius = glow
            ),
            radius = glow,
            center = center
        )

        // The cookie itself, in Orion's exact splash outline
        withTransform({
            scale(0.2f + 0.8f * blob, 0.2f + 0.8f * blob, pivot = center)
            rotate(blobRotation, pivot = center)
        }) {
            drawPath(blobPath(unit, ox, oy), markColor)
        }

        // The glyph group, in the vector's 0.6x group: Android scales about the group's own pivot
        // (50, 53.55) and then lifts it by -3.55, which lands the block dead centre of the
        // cookie. Reading it as "scale about 50, then lift" floats the glyphs 1.42 units too high.
        fun groupedPoint(px: Float, py: Float): Offset =
            Offset((50f + (px - 50f) * 0.6f) * unit + ox, (50f + (py - 53.55f) * 0.6f) * unit + oy)

        val trianglePath = Path().apply {
            val a = groupedPoint(39f, 52f)
            val b = groupedPoint(61f, 52f)
            val c1 = groupedPoint(63.8f, 52f)
            val c2 = groupedPoint(63.8f, 48.5f)
            val d = groupedPoint(51.8f, 29.5f)
            val e = groupedPoint(50f, 28f)
            val f = groupedPoint(48.2f, 29.5f)
            val g = groupedPoint(36.2f, 48.5f)
            val h = groupedPoint(36.2f, 52f)
            moveTo(a.x, a.y)
            lineTo(b.x, b.y)
            quadraticTo(c1.x, c1.y, c2.x, c2.y)
            lineTo(d.x, d.y)
            quadraticTo(e.x, e.y, f.x, f.y)
            lineTo(g.x, g.y)
            quadraticTo(h.x, h.y, a.x, a.y)
            close()
        }
        val triangleScale = triangle.coerceAtLeast(0f)
        // Android lifts the triangle 14 units and lets it drop back to rest while it pops in. The
        // lift rides on its own eased track — folding it into the scale would park the finished
        // triangle 14 units too high, jammed against the top of the cookie.
        val triangleLift = -14f * unit * (1f - FastOutSlowInEasing.transform(stage(0.30f, 0.65f)))
        withTransform({
            scale(triangleScale, triangleScale, pivot = groupedPoint(50f, 40f))
            translate(top = triangleLift)
        }) {
            drawPath(trianglePath, cutoutColor)
        }

        val circleCenter = groupedPoint(36.8f, 66.9f)
        drawCircle(
            color = cutoutColor,
            radius = 12.2f * 0.6f * circle * unit,
            center = circleCenter
        )

        val squareAnchor = groupedPoint(63.2f, 66.9f)
        val squareSize = 24.3f * 0.6f * square * unit
        // Android spins the square a quarter turn as it pops in and lands it upright.
        val squareSpin = -45f + 45f * FastOutSlowInEasing.transform(stage(0.54f, 0.90f))
        withTransform({ rotate(squareSpin, pivot = squareAnchor) }) {
            drawRoundRect(
                color = cutoutColor,
                topLeft = Offset(squareAnchor.x - squareSize / 2f, squareAnchor.y - squareSize / 2f),
                size = Size(squareSize, squareSize),
                cornerRadius = CornerRadius(3.9f * 0.6f * unit, 3.9f * 0.6f * unit)
            )
        }

        // The "RE" pill, the last thing to land
        withTransform({
            scale(re, re, pivot = Offset(50f * unit + ox, 71f * unit + oy))
            translate(top = 1.2f * unit)
        }) {
            // Orion's RE badge in the vector's own units: a 9x7 stadium with a 3.5 radius and the
            // letterforms at their original size - the mark is drawn larger than the phone draws
            // it, so the badge scales up with it and keeps the original proportions.
            val k = 1f
            val pillWidth = 9f * unit
            val pillHeight = 7f * unit
            val pillRadius = 3.5f * unit
            drawRoundRect(
                color = cutoutColor,
                topLeft = Offset(50f * unit + ox - pillWidth / 2f, 71f * unit + oy - pillHeight / 2f),
                size = Size(pillWidth, pillHeight),
                cornerRadius = CornerRadius(pillRadius, pillRadius)
            )
            fun g(px: Float, py: Float): Offset =
                Offset((50f + (px - 50f) * k) * unit + ox, (71f + (py - 71f) * k) * unit + oy)
            val glyph = Path().apply {
                // R: stem up, bowl, then the leg out to the foot
                val r1 = g(46.6f, 72.8f)
                val r2 = g(46.6f, 69.2f)
                val r3 = g(48.3f, 69.2f)
                val r4 = g(50.1f, 70.1f)
                val r5 = g(48.3f, 71f)
                val r6 = g(46.6f, 71f)
                val r7 = g(48.1f, 71f)
                val r8 = g(49.4f, 72.8f)
                moveTo(r1.x, r1.y)
                lineTo(r2.x, r2.y)
                lineTo(r3.x, r3.y)
                quadraticTo(r4.x, r4.y, r5.x, r5.y)
                lineTo(r6.x, r6.y)
                moveTo(r7.x, r7.y)
                lineTo(r8.x, r8.y)
                // E: spine, three arms
                val e1 = g(53.6f, 69.2f)
                val e2 = g(51.3f, 69.2f)
                val e3 = g(51.3f, 72.8f)
                val e4 = g(53.6f, 72.8f)
                val e5 = g(51.3f, 71f)
                val e6 = g(53.2f, 71f)
                moveTo(e1.x, e1.y)
                lineTo(e2.x, e2.y)
                lineTo(e3.x, e3.y)
                lineTo(e4.x, e4.y)
                moveTo(e5.x, e5.y)
                lineTo(e6.x, e6.y)
            }
            drawPath(
                path = glyph,
                color = markColor,
                style = Stroke(width = 0.9f * k * unit, join = StrokeJoin.Round, cap = StrokeCap.Round)
            )
        }
    }

        // The type scales with the mark, so the lockup reads as one piece at any window size.
        val wordSize = (markSize.value * 0.095f).coerceIn(16f, 32f).sp
        val subSize = (markSize.value * 0.052f).coerceIn(11f, 18f).sp
        Spacer(Modifier.height((markSize * 0.045f).coerceIn(10.dp, 28.dp)))
        Text(
            text = "ORION STORE",
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = wordSize,
                letterSpacing = (wordSize.value * 0.26f).sp
            ),
            fontWeight = FontWeight.Bold,
            color = markColor.copy(alpha = wordAlpha)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Open-source app store for Windows",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = subSize,
                letterSpacing = 1.5.sp
            ),
            color = markColor.copy(alpha = wordAlpha * 0.72f)
        )
    }
    }
}
