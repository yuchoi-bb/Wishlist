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
    /** "완료일" — date only, null means still in progress. */
    val completedAt: Long? = null,
) {
    val isCompleted: Boolean get() = completedAt != null

    /** "고민한 기간": elapsed time between startedAt and completedAt, or now if still in progress. */
    fun ponderedDurationMillis(now: Long): Long {
        val end = completedAt ?: now
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
