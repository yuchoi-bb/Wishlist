package com.wishlist.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.wishlist.app.util.monthOf

/**
 * The color a line takes when the table is sorted by 완료예정일: one hue per month, so the boundary
 * between one month's work and the next is visible without reading a single date. Twelve hues 30°
 * apart, which keeps neighbouring months clearly different.
 *
 * Null when the line has no date — there's no month to belong to.
 */
fun monthColor(epochMillis: Long?): Color? {
    val month = epochMillis?.let { monthOf(it) } ?: return null
    return Color.hsv(hue = (month - 1) * 30f, saturation = 0.55f, value = 0.85f)
}
