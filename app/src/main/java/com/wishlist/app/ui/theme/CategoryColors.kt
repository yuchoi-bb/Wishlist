package com.wishlist.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.absoluteValue

/**
 * Colors that identify a (대/중분류) at a glance in the main table.
 *
 * The hue comes from 대분류 alone, so everything in one 대분류 is visibly related; 중분류 only shifts
 * that hue lighter or darker, so sibling 중분류 stay distinguishable without looking unrelated. The
 * mapping is derived from the name itself rather than stored, so the same category keeps its color
 * on every device and across reinstalls without anything to sync.
 */
private val palette = listOf(
    Color(0xFF4F7CFF), // blue
    Color(0xFF2FA37A), // green
    Color(0xFFE0693E), // orange
    Color(0xFF8E5BD0), // purple
    Color(0xFFC0397B), // magenta
    Color(0xFF2A9AAF), // teal
    Color(0xFFB08A1E), // gold
    Color(0xFF5E6E8C), // slate
)

/** Null when there's no 대분류 to color by, so the row keeps the plain surface. */
fun categoryColor(major: String?, minor: String?): Color? {
    val majorKey = major?.takeIf { it.isNotBlank() } ?: return null
    val base = palette[majorKey.hashCode().absoluteValue % palette.size]
    val minorKey = minor?.takeIf { it.isNotBlank() } ?: return base
    // -0.24..+0.24 — five steps of shade per 대분류, enough to tell 중분류 apart while staying in
    // the same family.
    val shift = ((minorKey.hashCode().absoluteValue % 5) - 2) * 0.12f
    return when {
        shift > 0f -> lerp(base, Color.White, shift)
        shift < 0f -> lerp(base, Color.Black, -shift)
        else -> base
    }
}
