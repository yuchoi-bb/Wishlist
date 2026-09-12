package com.wishlist.app.share

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast

/**
 * Hands a 할 일 (or one 세부항목) to whichever calendar app the user has, opening its new-event
 * screen already filled in. Done with an insert intent rather than by writing to the calendar
 * provider directly, so Arc never needs the calendar permissions, and the user still sees and
 * confirms what gets added — and picks which calendar it lands in.
 */
fun Context.addToCalendar(title: String, description: String?, dateMillis: Long) {
    val intent = Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        putExtra(CalendarContract.Events.TITLE, title)
        description?.takeIf { it.isNotBlank() }?.let {
            putExtra(CalendarContract.Events.DESCRIPTION, it)
        }
        // 완료예정일 is a day, so it goes in as an all-day event ending the next midnight.
        putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, dateMillis)
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, dateMillis + DAY_MILLIS)
    }
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, "캘린더 앱을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
    }
}

private const val DAY_MILLIS = 24L * 60 * 60 * 1000
