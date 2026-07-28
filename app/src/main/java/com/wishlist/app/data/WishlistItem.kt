package com.wishlist.app.data

/**
 * One checkable detail row under a wishlist item, and the unit the main table sorts by.
 * Its [endDate] is this step's own deadline, sitting inside the parent item's overall 최종 종료일.
 */
data class SubItem(
    val title: String = "",
    val done: Boolean = false,
    val endDate: Long? = null,
)

/** Stored as a Firestore document under users/{uid}/wishlist_items/{id}; id is "" for a not-yet-saved item. */
data class WishlistItem(
    val id: String = "",
    val title: String = "",
    /** Free-form notes for this item. */
    val memo: String? = null,
    /** Checkable detail rows under this item. */
    val subItems: List<SubItem> = emptyList(),
    val majorCategory: String? = null,
    val minorCategory: String? = null,
    /** "시작일" — date only (local start-of-day), defaults to the creation date, user-editable. */
    val startedAt: Long = 0,
    /**
     * "최종 종료일" — the date the whole item is meant to be finished by. Individual 세부항목 carry
     * their own earlier deadlines. Being finished is tracked separately by [isDone], so a future
     * target date doesn't make an item look complete.
     */
    val endDate: Long? = null,
    /** "완료" — whether the task is actually finished. */
    val isDone: Boolean = false,
    /** "우선순위" — 1 (highest) to 3 (lowest), set per item rather than per 세부항목. */
    val priority: Int = DEFAULT_PRIORITY,
    /**
     * Creation order, kept only so the value survives round-trips; the table is ordered by the
     * chosen sort field rather than by hand.
     */
    val position: Long = 0,
) {
    val isCompleted: Boolean get() = isDone

    val doneSubItemCount: Int get() = subItems.count { it.done }

    /** Key identifying which category group this item belongs to for grouping + per-group sort. */
    val categoryKey: String
        get() {
            val major = majorCategory?.takeIf { it.isNotBlank() }
            val minor = minorCategory?.takeIf { it.isNotBlank() }
            return when {
                major == null && minor == null -> UNCATEGORIZED_KEY
                minor == null -> major!!
                else -> "$major/$minor"
            }
        }

    companion object {
        const val UNCATEGORIZED_KEY = "__uncategorized__"
        const val DEFAULT_PRIORITY = 2
        val PRIORITIES = listOf(1, 2, 3)
    }
}
