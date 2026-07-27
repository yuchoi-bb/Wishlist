package com.wishlist.app.data

/** Stored as a Firestore document under users/{uid}/wishlist_items/{id}; id is "" for a not-yet-saved item. */
data class WishlistItem(
    val id: String = "",
    val title: String = "",
    val majorCategory: String? = null,
    val minorCategory: String? = null,
    /** "고민을 시작한 시간" — defaults to creation time, user-editable. */
    val startedAt: Long = 0,
    /** "완료일자" — null means still in progress. Set/cleared by the user or the complete checkbox. */
    val completedAt: Long? = null,
) {
    val isCompleted: Boolean get() = completedAt != null

    /** "고민한 기간": elapsed time between startedAt and completedAt, or now if still in progress. */
    fun ponderedDurationMillis(now: Long): Long {
        val end = completedAt ?: now
        return (end - startedAt).coerceAtLeast(0)
    }

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
