package com.wishlist.app.share

import com.wishlist.app.data.SubItem

/**
 * Turns a block of text — typically an answer pasted or shared out of Claude, ChatGPT or Gemini —
 * into a 할 일 with its 세부항목 already filled in.
 *
 * What those apps hand over is a list: a line naming the thing, then bullets or numbered steps
 * under it, often with a date on a step. [toSharedDraft] on its own keeps only the first line as
 * the 항목 and buries the rest in 메모, which leaves every 세부항목 to be typed again by hand. This
 * reads the shape instead.
 *
 * Nothing here is model-specific and no network call is involved: it's the Markdown the three of
 * them happen to agree on, so a hand-written list pastes in just as well. Returns null when the
 * text is no list at all, leaving the plain one-line reading to the caller.
 */
internal fun parseDiscussion(text: String): SharedDraft? {
    val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')

    var heading: String? = null
    var headingDate: Long? = null
    val subItems = mutableListOf<SubItem>()
    val memoLines = mutableListOf<String>()

    for (raw in lines) {
        val line = raw.trim()
        if (line.isBlank() || RULE.matches(line)) continue

        val step = line.asStep()
        if (step != null) {
            if (step.title.isNotBlank()) subItems += step
            continue
        }

        val clean = line.replace(HEADING, "").stripMarkdown().trimEnd(':', '：').trim()
        if (clean.isBlank()) continue
        if (heading == null) {
            heading = clean
            headingDate = findDate(line)
        } else {
            memoLines += clean
        }
    }

    if (heading == null && subItems.isEmpty()) return null

    val title: String
    val titleDate: Long?
    if (heading != null) {
        title = heading
        titleDate = headingDate
    } else {
        // A bare list with no line naming it: the first step becomes the 항목, so the item still
        // has a name that came from the text rather than one invented here.
        val first = subItems.removeAt(0)
        title = first.title
        titleDate = first.endDate
    }

    // One line and nothing else is an ordinary share, not a discussion. Handing back null lets the
    // caller apply its own 메모 and date rules instead of this one dressing it up as a list.
    if (subItems.isEmpty() && memoLines.isEmpty()) return null

    return SharedDraft(
        title = title.take(MAX_TITLE),
        memo = memoLines.joinToString("\n").ifBlank { null },
        // The 항목's 최종 종료일: its own line if that names a date, else the last of its steps,
        // since the whole thing isn't finished until the latest one is.
        endDate = titleDate ?: subItems.mapNotNull { it.endDate }.maxOrNull(),
        subItems = subItems.toList(),
    )
}

/** The line read as a 세부항목, or null when it isn't a list item at all. */
private fun String.asStep(): SubItem? {
    val marker = BULLET.find(this) ?: return null
    var rest = substring(marker.value.length)

    // "- [x] 비자 신청" arrives already ticked, which is worth keeping: a discussion usually records
    // what is done alongside what still isn't.
    var done = false
    CHECKBOX.find(rest)?.let { box ->
        done = box.groupValues[1].equals("x", ignoreCase = true)
        rest = rest.substring(box.value.length)
    }

    return SubItem(title = rest.stripMarkdown().take(MAX_TITLE), done = done, endDate = findDate(rest))
}

/** Markdown emphasis, code ticks and link syntax, so "**비자 신청**" doesn't reach the table as-is. */
private fun String.stripMarkdown(): String = replace(LINK, "$1").replace(EMPHASIS, "$2").trim()

private val BULLET = Regex("""^\s*(?:[-*+•·‣▪–—]|\d{1,3}[.)]|[①-⑳])\s+""")
private val CHECKBOX = Regex("""^\[([ xX])\]\s*""")
private val HEADING = Regex("""^#{1,6}\s*""")
private val RULE = Regex("""^\s*([-*_])\s*(\1\s*){2,}$""")

/**
 * Only the asterisk and backtick forms. Underscore emphasis is left alone on purpose: `_` is a word
 * character, so stripping it would eat the middle of an identifier like some_table_name.
 *
 * Both of these are written with `\]` rather than a bare `]` inside a character class. Java's regex
 * — unlike POSIX — does not read a leading `]` as a literal, and these are top-level vals, so a
 * malformed one would throw the first time anything is parsed.
 */
private val EMPHASIS = Regex("""(\*\*|\*|`)(.+?)\1""")
private val LINK = Regex("""\[([^\]]+)\]\([^)]*\)""")
