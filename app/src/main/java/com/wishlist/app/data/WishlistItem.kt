package com.wishlist.app.data

/** One checkable detail row under a wishlist item. */
data class SubItem(
    val title: String = "",
    val done: Boolean = false,
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
     * "종료일" — the date this is *meant* to be finished by, date only, null if none is set.
     * Being finished is tracked separately by [isDone], so a future target date doesn't make an
     * item look complete.
     */
    val endDate: Long? = null,
    /** "완료" — whether the task is actually finished. */
    val isDone: Boolean = false,
    /**
     * Rank within its category group under SortField.MANUAL. A reorder rewrites these as 0,1,2…,
     * while newly created items get a millisecond timestamp so they land after anything already
     * arranged by hand.
     */
    val position: Long = 0,
) {
    val isCompleted: Boolean get() = isDone

    /** Elapsed time from 시작일 to the 종료일 (or now while unfinished). */
    fun ponderedDurationMillis(now: Long): Long {
        val end = if (isDone) endDate ?: now else now
        return (end - startedAt).coerceAtLeast(0)
    }

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
    }
}
