package com.wishlist.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.wishlist.app.R

/**
 * The serif the app sets its dates, counts and headings in. A monospaced figure reads as data —
 * something a machine produced — while a serif figure reads as a note someone kept, which is what
 * this app is for.
 *
 * It is bundled cut down to the glyphs the app's own chrome uses (Latin, figures, punctuation and
 * the Hangul in the source's own strings), because the full Korean face is 8 MB. It is therefore
 * only ever applied to text the app itself writes — never to a 할 일 title or any other user text.
 * A glyph outside the subset still renders: Android falls back to the system font per glyph.
 */
val Serif = FontFamily(
    Font(R.font.gowun_batang_regular, FontWeight.Normal),
    Font(R.font.gowun_batang_bold, FontWeight.Bold),
)

/**
 * Body and UI text stays on the platform's own Korean face. Bundling a second full Korean family
 * would have cost several megabytes to say very little the system font doesn't already say.
 */
private val Sans = FontFamily.Default

/**
 * Roomier than Material's defaults on purpose: the screens this app spends its time on are lists of
 * short lines, and the space between them is what keeps a long list calm.
 */
val WishlistTypography = Typography(
    displaySmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 23.sp, lineHeight = 31.sp),
    headlineSmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontSize = 13.5.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 11.5.sp),
    labelSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.5.sp,
        letterSpacing = 0.08.em,
    ),
)
