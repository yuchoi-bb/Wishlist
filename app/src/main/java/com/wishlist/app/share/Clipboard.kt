package com.wishlist.app.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.core.content.getSystemService

/**
 * The clipboard's text, or null when there is nothing to paste.
 *
 * The share sheet covers apps that offer one, but an AI chat often only gives 복사 — and on some
 * builds that's the only way text leaves it at all. Reading the clipboard here keeps the two routes
 * equivalent: both end at [draftFromText], so a 할 일 arrives the same whichever was used.
 *
 * Android 12 and up shows the user a toast naming the app that read the clipboard; that notice is
 * the point, and only ever fires on the tap that asked for it.
 */
fun Context.clipboardText(): String? {
    val clip = getSystemService<ClipboardManager>()?.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    // coerceToText rather than the text field: it also renders HTML or a URI the source put there.
    return (0 until clip.itemCount)
        .mapNotNull { clip.getItemAt(it).coerceToText(this)?.toString() }
        .joinToString("\n")
        .trim()
        .takeIf { it.isNotBlank() }
}

/**
 * Puts [text] on the clipboard under [label].
 *
 * Used for the account's own uid, which is the only handle one person can pass another to be shared
 * a 할 일: a client cannot look an account up by email address, so the uid is typed or pasted, and
 * twenty-eight characters read off a screen by hand would be a transcription error waiting to
 * happen.
 */
fun Context.copyToClipboard(label: String, text: String) {
    val manager = getSystemService<ClipboardManager>() ?: return
    manager.setPrimaryClip(ClipData.newPlainText(label, text))
}
