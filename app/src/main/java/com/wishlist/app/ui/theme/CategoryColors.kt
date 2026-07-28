package com.wishlist.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.wishlist.app.data.CategoryColorPref
import kotlin.math.absoluteValue

/**
 * The colors a (대/중분류) can take in the main table. Chosen to stay legible as a background tint
 * in both light and dark themes; prefs store an index into this list rather than a raw color.
 */
val CATEGORY_PALETTE: List<Color> = listOf(
    Color(0xFF4F7CFF), // blue
    Color(0xFF2FA37A), // green
    Color(0xFFE0693E), // orange
    Color(0xFF8E5BD0), // purple
    Color(0xFFC0397B), // magenta
    Color(0xFF2A9AAF), // teal
    Color(0xFFB08A1E), // gold
    Color(0xFF5E6E8C), // slate
)

/**
 * The color identifying a (대/중분류), or null when there's no 대분류 to color by.
 *
 * A user-picked color in [prefs] wins: one set on the 중분류 is used exactly, one set on the 대분류
 * colors the whole 대분류. Anything not picked falls back to a color derived from the name itself —
 * the hue from 대분류, shaded lighter or darker by 중분류 — so categories are distinguishable from
 * the moment they're created, with nothing to configure and nothing to sync.
 */
fun categoryColor(
    major: String?,
    minor: String?,
    prefs: List<CategoryColorPref> = emptyList(),
): Color? {
    val majorKey = major?.takeIf { it.isNotBlank() } ?: return null
    val minorKey = minor?.takeIf { it.isNotBlank() }

    if (minorKey != null) {
        prefs.paletteColor(majorKey, minorKey)?.let { return it }
    }
    val base = prefs.paletteColor(majorKey, null)
        ?: CATEGORY_PALETTE[majorKey.hashCode().absoluteValue % CATEGORY_PALETTE.size]
    if (minorKey == null) return base

    // -0.24..+0.24 — five steps of shade, enough to tell 중분류 apart while keeping the 대분류 family.
    val shift = ((minorKey.hashCode().absoluteValue % 5) - 2) * 0.12f
    return when {
        shift > 0f -> lerp(base, Color.White, shift)
        shift < 0f -> lerp(base, Color.Black, -shift)
        else -> base
    }
}

/** The palette index the user picked for this exact category, or null if they haven't. */
fun List<CategoryColorPref>.paletteIndexFor(major: String, minor: String?): Int? =
    firstOrNull { it.major == major && it.minor == minor }?.paletteIndex

private fun List<CategoryColorPref>.paletteColor(major: String, minor: String?): Color? =
    paletteIndexFor(major, minor)?.let { CATEGORY_PALETTE[it.mod(CATEGORY_PALETTE.size)] }
