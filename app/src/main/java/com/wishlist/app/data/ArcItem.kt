package com.wishlist.app.data

/**
 * One checkable detail row under a wishlist item, and the unit the main table sorts by.
 * Its [endDate] is this step's own deadline, sitting inside the parent item's overall 최종 종료일.
 */
data class SubItem(
    val title: String = "",
    val done: Boolean = false,
    val endDate: Long? = null,
    /**
     * The id of another 항목 this step stands for, or null for an ordinary step.
     *
     * A linked step is how a 할 일 holds a 할 일: 호주여행계획 can carry 호주여행수영 as one of its
     * 세부항목, and tapping it opens that item's own 세부항목. The target is a normal 항목 — it keeps
     * its place in the main table and can be opened from there too, so this adds a way in rather
     * than a second kind of thing.
     *
     * Only the id is stored. [title] is a copy of the target's name taken when the link was made,
     * which is what the row falls back to while the target is still loading, or once it is gone.
     */
    val linkedItemId: String? = null,
)

/**
 * Stored as a Firestore document under users/{uid}/wishlist_items/{id} while it is private, or
 * shared_items/{id} once it is shared; id is "" for a not-yet-saved item.
 */
data class ArcItem(
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
    /**
     * The uid of whoever owns a shared 할 일, and "" while it is private.
     *
     * The owner is the only one who can change [members] or delete the item outright; everyone in
     * [members] edits its contents, because ticking a 세부항목 is the point of sharing one.
     */
    val ownerUid: String = "",
    /**
     * Every uid that may see this 할 일, the owner included — and empty for a private one.
     *
     * Non-empty is exactly what it means to be shared, and decides where the document lives:
     * shared_items/{id} when there is a member list, users/{uid}/wishlist_items/{id} when there
     * isn't. Sharing moves it between the two, keeping its id so a 세부항목 linked to it still
     * resolves.
     */
    val members: List<String> = emptyList(),
) {
    val isCompleted: Boolean get() = isDone

    /** True once this 할 일 has a member list, which is also to say it lives in shared_items. */
    val isShared: Boolean get() = members.isNotEmpty()

    /** Whether [uid] may change who this 할 일 is shared with, or delete it for everyone. */
    fun isOwnedBy(uid: String): Boolean = !isShared || ownerUid == uid

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
