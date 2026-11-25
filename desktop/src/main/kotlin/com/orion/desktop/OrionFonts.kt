package com.orion.desktop

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font as ResourceBackedFont

/**
 * Google Sans (SIL Open Font License) ships inside the desktop app, so the typography never
 * depends on the font being installed on the machine.
 *
 * The files are addressed as classpath resources: the desktop font loader reads them straight
 * out of the app jar, and Compose picks the closest weight per style. If a resource is missing
 * the family falls back to the platform font, so the UI can never end up without type.
 */
private val ORION_FONT_RESOURCES = listOf(
    "fonts/GoogleSans-Regular.ttf" to FontWeight.Normal,
    "fonts/GoogleSans-Medium.ttf" to FontWeight.Medium,
    "fonts/GoogleSans-SemiBold.ttf" to FontWeight.SemiBold,
    "fonts/GoogleSans-Bold.ttf" to FontWeight.Bold
)

/** Anchor for the app classloader that owns the bundled font resources. */
private object OrionFontResources

@Composable
fun rememberOrionFontFamily(): FontFamily = remember {
    // The app classloader is the one that sees the bundled resources; the thread context
    // classloader can be the launcher's on the UI thread.
    val loader = OrionFontResources::class.java.classLoader
    val available = ORION_FONT_RESOURCES.filter { (resource, _) -> loader.getResource(resource) != null }
    if (available.isEmpty()) {
        FontFamily.Default
    } else {
        FontFamily(available.map { (resource, weight) -> ResourceBackedFont(resource, weight) })
    }
}

/** The Material type ramp, re-typed in Google Sans. */
fun Typography.withFontFamily(family: FontFamily): Typography = Typography(
    displayLarge = displayLarge.with(family),
    displayMedium = displayMedium.with(family),
    displaySmall = displaySmall.with(family),
    headlineLarge = headlineLarge.with(family),
    headlineMedium = headlineMedium.with(family),
    headlineSmall = headlineSmall.with(family),
    titleLarge = titleLarge.with(family),
    titleMedium = titleMedium.with(family),
    titleSmall = titleSmall.with(family),
    bodyLarge = bodyLarge.with(family),
    bodyMedium = bodyMedium.with(family),
    bodySmall = bodySmall.with(family),
    labelLarge = labelLarge.with(family),
    labelMedium = labelMedium.with(family),
    labelSmall = labelSmall.with(family)
)

private fun TextStyle.with(family: FontFamily): TextStyle = copy(fontFamily = family)
