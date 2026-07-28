package com.wishlist.app.data

/**
 * How the main table is ordered. Sorting applies to the whole table at once — each row is one
 * 세부항목 — rather than being chosen separately per category.
 */
enum class SortField {
    /** 세부항목's own 종료일, falling back to its item's 최종 종료일. */
    END_DATE,
    PRIORITY,
    MAJOR_CATEGORY,
    MINOR_CATEGORY,
    /** 세부항목 name, i.e. the 소분류. */
    SUB_ITEM,
    TITLE,
    START_DATE,
}
