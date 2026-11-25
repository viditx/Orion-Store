package com.orion.store.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * A stable hue for a name, so the same app always gets the same fallback colour.
 * Ported from the Android app's Common.kt.
 */
fun hue(seed: String): Float {
    var h = 0
    seed.forEach { h = (h shl 5) - h + it.code }
    return (abs(h) % 360).toFloat()
}

/** HSL colour helper, ported from the Android app's Common.kt. */
fun hsl(h: Float, s: Float, l: Float, a: Float = 1f) = Color.hsl(h, s, l, a)
