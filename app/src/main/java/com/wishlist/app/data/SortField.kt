package com.wishlist.app.data

/** Sort criteria a category group can be sorted by, independently of every other group. */
enum class SortField {
    COMPLETED_AT,
    STARTED_AT,
    DURATION,
    TITLE,
}

enum class StatusFilter {
    ALL,
    IN_PROGRESS,
    COMPLETED,
}
