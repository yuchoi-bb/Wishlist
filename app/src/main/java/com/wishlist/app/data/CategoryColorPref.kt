package com.wishlist.app.data

/**
 * A color the user picked for a category, stored per account so every device shows the same one.
 * [minor] null means the pref is for the 대분류 itself; otherwise it's for that one 중분류 inside it.
 * [paletteIndex] indexes the app's palette rather than holding a raw ARGB value, so the colors stay
 * within a set that reads well in both light and dark themes.
 */
data class CategoryColorPref(
    val major: String,
    val minor: String? = null,
    val paletteIndex: Int = 0,
)
