package com.wishlist.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.wishlist.app.util.monthOf

/**
 * The color a line takes when the table is sorted by 완료예정일: one hue per month, so the boundary
 * between one month's work and the next is visible without reading a single date. Twelve hues 30°
 * apart, which keeps neighbouring months clearly different.
 *
 * [dark] picks a brighter, slightly less saturated version: the tint is painted over the surface at
 * a low alpha, and a color that reads well over white disappears into a dark background.
 *
 * Null when the line has no date — there's no month to belong to.
 */
fun monthColor(epochMillis: Long?, dark: Boolean): Color? {
    val month = epochMillis?.let { monthOf(it) } ?: return null
    return Color.hsv(
        hue = (month - 1) * 30f,
        saturation = if (dark) 0.6f else 0.8f,
        value = if (dark) 1f else 0.85f,
    )
}
