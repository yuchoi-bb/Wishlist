package com.wishlist.app.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val dateFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")

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

/** For things that happen at a moment rather than on a day, e.g. when a backup last ran. */
fun formatDateTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(dateTimeFormatter)

/**
 * Local start-of-day of the last day of the month [monthsAhead] from today — the target date behind
 * the 월말 shortcuts. Offset 0 is 이달, 1 is next month, and so on.
 */
fun endOfMonthMillis(monthsAhead: Int, now: Long = System.currentTimeMillis()): Long {
    val zone = ZoneId.systemDefault()
    val month = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusMonths(monthsAhead.toLong())
    return month.withDayOfMonth(month.lengthOfMonth()).atStartOfDay(zone).toInstant().toEpochMilli()
}

/**
 * Label for a 월말 shortcut, kept to two characters: 월말 for the current month, then the month
 * followed by 말. Months 10–12 would spill to three characters as digits, so they use A/B/C.
 */
fun monthEndLabel(monthsAhead: Int, now: Long = System.currentTimeMillis()): String {
    if (monthsAhead == 0) return "월말"
    val zone = ZoneId.systemDefault()
    val month = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        .plusMonths(monthsAhead.toLong())
        .monthValue
    val symbol = when (month) {
        10 -> "A"
        11 -> "B"
        12 -> "C"
        else -> month.toString()
    }
    // Braces are required: Hangul is an identifier character, so "$symbol말" would parse as one name.
    return "${symbol}말"
}

/** Countdown to a 종료일: "D-3" with days to go, "D-DAY" today, "D+2" once it's past. */
fun formatRemainingDays(endDate: Long?, now: Long = System.currentTimeMillis()): String {
    if (endDate == null) return "-"
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val target = Instant.ofEpochMilli(endDate).atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(today, target)
    return when {
        days > 0 -> "D-$days"
        days == 0L -> "D-DAY"
        else -> "D+${-days}"
    }
}
