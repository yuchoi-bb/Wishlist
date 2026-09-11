package com.wishlist.app.share

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Hands one 할 일 — a whole 항목 or a single 세부항목 — to whichever to-do app the user has, through
 * the system share sheet.
 *
 * Android has no standard provider or insert intent for to-do lists the way it has
 * [CalendarContract][android.provider.CalendarContract] for events, so there is nothing to fill in
 * directly the way [addToCalendar] does. ACTION_SEND is the one entry point every to-do app accepts,
 * and going through the chooser means Wishlist never has to name a particular app — it also needs no
 * permission and no `<queries>` entry, because the chooser is a system component rather than an
 * activity Wishlist has to resolve for itself.
 *
 * The text is shaped the way [toSharedDraft] reads one back — title on the first line, details under
 * it, the date written so the parser finds it — so a 할 일 sent out and shared back into Wishlist on
 * another device survives the round trip.
 */
fun Context.sendToTaskApp(title: String, memo: String?, dateMillis: Long?) {
    val body = buildString {
        append(title)
        dateMillis?.let { append("\n예정일: ").append(isoDate(it)) }
        memo?.trim()?.takeIf { it.isNotBlank() }?.let { append('\n').append(it) }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        // An app that keeps a task's name apart from its note takes the subject as the name; one
        // that only reads EXTRA_TEXT still finds the name on the first line.
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    try {
        startActivity(Intent.createChooser(intent, "할 일 앱으로 보내기"))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, "보낼 수 있는 앱을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
    }
}

/**
 * The date as the handoff writes it. Kept here rather than with the display formatters because it's
 * an interchange format, not something shown on screen: unambiguous to a person in any locale, and
 * the shape [toSharedDraft]'s date search reads back.
 */
private fun isoDate(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(ISO_DATE)

private val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
