package com.wishlist.app.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")

/**
 * 시작일/완료일 are dates, not timestamps, so every stored value is normalized to local
 * start-of-day. That keeps day-difference math exact and comparisons stable.
 */
fun Long.toStartOfDayMillis(): Long =
    Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

fun todayStartOfDayMillis(): Long = System.currentTimeMillis().toStartOfDayMillis()

fun formatDate(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(dateFormatter)
