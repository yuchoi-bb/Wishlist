package com.wishlist.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.wishlist.app.util.daysUntil

/** How a 완료예정일 stands relative to today, and the colors that say so at a glance. */
enum class DueState {
    /** Finished — no deadline pressure left, whatever the date says. */
    DONE,

    /** The date has passed. */
    OVERDUE,

    /** Due today. */
    TODAY,

    /** Within a week. */
    SOON,

    /** Further out, or no date at all. */
    LATER,
}

/** A chip's two colors: what it's painted with, and what reads on top of it. */
data class DueColors(val container: Color, val content: Color)

fun dueStateOf(endDate: Long?, isDone: Boolean): DueState {
    if (isDone) return DueState.DONE
    val days = endDate?.let { daysUntil(it) } ?: return DueState.LATER
    return when {
        days < 0 -> DueState.OVERDUE
        days == 0L -> DueState.TODAY
        days <= SOON_DAYS -> DueState.SOON
        else -> DueState.LATER
    }
}

/**
 * Colors for a 남은날짜 chip. Everything used to be one shade of the theme's primary, which said
 * nothing about urgency — three days overdue looked exactly like three months out. Now the four
 * states are distinct, with a darker container and lighter text on a dark surface so the chip keeps
 * the same weight in both themes.
 */
fun dueColors(state: DueState, dark: Boolean): DueColors = when (state) {
    DueState.OVERDUE -> if (dark) {
        DueColors(Color(0xFF4A1D1F), Color(0xFFFF9A9E))
    } else {
        DueColors(Color(0xFFFDECEC), Color(0xFFB4232A))
    }

    DueState.TODAY -> if (dark) {
        DueColors(Color(0xFF1B5E20), Color(0xFFE8F5E9))
    } else {
        DueColors(Color(0xFF2E7D32), Color(0xFFFFFFFF))
    }

    DueState.SOON -> if (dark) {
        DueColors(Color(0xFF4A3611), Color(0xFFFFCF7A))
    } else {
        DueColors(Color(0xFFFFF4E5), Color(0xFF9A5B00))
    }

    DueState.LATER -> if (dark) {
        DueColors(Color(0x1FFFFFFF), Color(0xFFB9C0C3))
    } else {
        DueColors(Color(0xFFF2F4F3), Color(0xFF5B6469))
    }

    DueState.DONE -> if (dark) {
        DueColors(Color.Transparent, Color(0xFF7E878B))
    } else {
        DueColors(Color.Transparent, Color(0xFFA2AAAE))
    }
}

/** 우선순위 chips: P1 carries the brand tint, P2 is quiet, P3 is barely there. */
fun priorityColors(priority: Int, dark: Boolean): DueColors = when (priority) {
    1 -> if (dark) {
        DueColors(Color(0xFF1E3A22), Color(0xFF9CD3A2))
    } else {
        DueColors(Color(0xFFE8F0E9), Color(0xFF1B5E20))
    }

    2 -> if (dark) {
        DueColors(Color(0x1FFFFFFF), Color(0xFFB9C0C3))
    } else {
        DueColors(Color(0xFFF2F4F3), Color(0xFF5B6469))
    }

    else -> if (dark) {
        DueColors(Color.Transparent, Color(0xFF7E878B))
    } else {
        DueColors(Color.Transparent, Color(0xFF8A9399))
    }
}

/** A week — near enough that it should look different from "later". */
private const val SOON_DAYS = 7L
