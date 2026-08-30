package com.wishlist.app.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

// Two-digit year: the table has to fit eight columns on a phone, and the century is never in doubt.
private val dateFormatter = DateTimeFormatter.ofPattern("yy.MM.dd")
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

/** "8월 28일 금요일" — today, written the way a person says it. */
fun formatToday(now: Long = System.currentTimeMillis()): String {
    val date = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
    val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)
    return "${date.monthValue}월 ${date.dayOfMonth}일 $weekday"
}

/** "2026년 8월" — the band a month-grouped view puts above a run of rows. */
fun formatYearMonth(epochMillis: Long): String {
    val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    return "${date.year}년 ${date.monthValue}월"
}

/** Calendar month (1-12) a stored date falls in, in the device's own time zone. */
fun monthOf(epochMillis: Long): Int =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).monthValue

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

/** Whole days from today to [endDate] — negative once the date has passed. */
fun daysUntil(endDate: Long, now: Long = System.currentTimeMillis()): Long {
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val target = Instant.ofEpochMilli(endDate).atZone(zone).toLocalDate()
    return ChronoUnit.DAYS.between(today, target)
}

/** Countdown to a 종료일: "D-3" with days to go, "오늘" on the day, "D+2" once it's past. */
fun formatRemainingDays(endDate: Long?, now: Long = System.currentTimeMillis()): String {
    if (endDate == null) return "-"
    val days = daysUntil(endDate, now)
    return when {
        days > 0 -> "D-$days"
        days == 0L -> "오늘"
        else -> "D+${-days}"
    }
}
