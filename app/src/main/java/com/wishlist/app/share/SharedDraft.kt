package com.wishlist.app.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.wishlist.app.util.toStartOfDayMillis
import java.time.LocalDate
import java.time.ZoneId

/** A 할 일 sketched out from something another app shared, ready to open the editor with. */
data class SharedDraft(
    val title: String,
    val memo: String?,
    val endDate: Long?,
)

/**
 * Turns an ACTION_SEND from another app into a draft 할 일.
 *
 * Calendar apps share an event in one of two shapes: a .ics attachment, which carries the title and
 * date in machine-readable form, or plain text meant for a human. Both are handled — the .ics first
 * because it's exact — and anything else shared as text still becomes an item with the text as its
 * title, which is what makes Arc useful from any app's share sheet.
 */
fun Intent.toSharedDraft(context: Context): SharedDraft? {
    if (action != Intent.ACTION_SEND) return null

    readStream(context)?.let { fromIcs(it) }?.let { return it }

    val subject = getStringExtra(Intent.EXTRA_SUBJECT)?.trim()?.takeIf { it.isNotBlank() }
    val body = getStringExtra(Intent.EXTRA_TEXT)?.trim()?.takeIf { it.isNotBlank() }
    if (subject == null && body == null) return null

    // A shared event's first line is its title; the rest is when and where, which belongs in 메모.
    val title = subject ?: body!!.lineSequence().first().trim()
    val memo = when {
        // A sender that names the item in both the subject and the first line of the body — which
        // is what Arc's own 할 일 앱 handoff does, so that apps reading only one of the two
        // still get the name — would otherwise leave the title repeated at the top of 메모.
        subject != null -> body?.withoutTitleLine(subject)
        body!!.lineSequence().count() > 1 -> body.substringAfter('\n').trim().ifBlank { null }
        else -> null
    }
    return SharedDraft(title = title.take(MAX_TITLE), memo = memo, endDate = body?.let(::findDate))
}

/**
 * The body with a leading line that just repeats [title] dropped, or null once nothing is left. The
 * explicit "" fallback matters: [substringAfter] hands back the whole string when the delimiter is
 * missing, which would keep a one-line body that was only ever the title.
 */
private fun String.withoutTitleLine(title: String): String? {
    val rest = if (lineSequence().first().trim() == title) substringAfter('\n', "") else this
    return rest.trim().ifBlank { null }
}

private fun Intent.readStream(context: Context): String? {
    val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(Intent.EXTRA_STREAM)
    }
    uri ?: return null
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
    }.getOrNull()
}

/**
 * Pulls SUMMARY and DTSTART out of an iCalendar payload. Only the date part of DTSTART is kept —
 * 완료예정일 is a day, not a moment — so a UTC timestamp is read as the day it names rather than
 * being shifted into the local zone.
 */
private fun fromIcs(text: String): SharedDraft? {
    if (!text.contains("BEGIN:VEVENT")) return null
    // RFC 5545 folds long lines by starting the continuation with a space.
    val unfolded = text.replace("\r\n ", "").replace("\n ", "")
    val summary = Regex("(?m)^SUMMARY[^:\\r\\n]*:(.*)$").find(unfolded)?.groupValues?.get(1)?.trim()
    val start = Regex("(?m)^DTSTART[^:\\r\\n]*:(\\d{8})").find(unfolded)?.groupValues?.get(1)
    if (summary.isNullOrBlank() && start == null) return null

    val description = Regex("(?m)^DESCRIPTION[^:\\r\\n]*:(.*)$").find(unfolded)?.groupValues?.get(1)?.trim()
    return SharedDraft(
        title = (summary?.takeIf { it.isNotBlank() } ?: "공유된 일정").take(MAX_TITLE),
        memo = description?.takeIf { it.isNotBlank() }?.replace("\\n", "\n"),
        endDate = start?.let { dateOf(it.substring(0, 4), it.substring(4, 6), it.substring(6, 8)) },
    )
}

/** First date in the shared text, in any of the formats a Korean or ISO calendar entry uses. */
private fun findDate(text: String): Long? {
    val match = Regex("(\\d{4})\\s*[-./년]\\s*(\\d{1,2})\\s*[-./월]\\s*(\\d{1,2})").find(text) ?: return null
    val (year, month, day) = match.destructured
    return dateOf(year, month, day)
}

private fun dateOf(year: String, month: String, day: String): Long? = runCatching {
    LocalDate.of(year.toInt(), month.toInt(), day.toInt())
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
        .toStartOfDayMillis()
}.getOrNull()

private const val MAX_TITLE = 120
