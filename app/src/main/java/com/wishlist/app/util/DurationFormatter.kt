package com.wishlist.app.util

import java.util.concurrent.TimeUnit

/** Formats a "고민한 기간" duration into short Korean text, e.g. "3일 4시간", "12분". */
fun formatPonderedDuration(millis: Long): String {
    val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val days = totalMinutes / (24 * 60)
    val hours = (totalMinutes % (24 * 60)) / 60
    val minutes = totalMinutes % 60

    return when {
        days > 0 -> "${days}일 ${hours}시간"
        hours > 0 -> "${hours}시간 ${minutes}분"
        else -> "${minutes}분"
    }
}
