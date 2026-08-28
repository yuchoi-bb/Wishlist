package com.wishlist.app.data

/**
 * How the main table is ordered. One sort applies to the whole table at once.
 *
 * Two kinds of field: those that describe a 세부항목 sort the rows one by one, so a single item's
 * 세부항목 can end up far apart; those that describe the 항목 or its (대/중분류) sort whole items and
 * keep every 세부항목 of an item together beneath it (see [keepsItemTogether]).
 */
enum class SortField {
    /** 세부항목's own 완료예정일, falling back to its item's 최종 종료일. Sorted per 세부항목. */
    END_DATE,

    /** 세부항목 name, i.e. the 소분류. Sorted per 세부항목. */
    SUB_ITEM,

    /** 대분류 + 중분류 treated as a single key, so a category is never split in two. */
    CATEGORY,

    /** 항목(할 일) name. */
    TITLE,

    /** 우선순위, which belongs to the 항목. */
    PRIORITY,

    /** 시작일, which belongs to the 항목. */
    START_DATE,
    ;

    /** What this field is called on screen — in the sort menu and in the table's header. */
    val label: String
        get() = when (this) {
            END_DATE -> "예정일"
            SUB_ITEM -> "세부항목"
            CATEGORY -> "대/중분류"
            TITLE -> "할 일"
            PRIORITY -> "순위"
            START_DATE -> "시작일"
        }

    /**
     * True when this field belongs to the 항목 rather than to a single 세부항목, meaning the item is
     * moved as one block and its 세부항목 follow it in their own order.
     */
    val keepsItemTogether: Boolean
        get() = when (this) {
            END_DATE, SUB_ITEM -> false
            CATEGORY, TITLE, PRIORITY, START_DATE -> true
        }

    companion object {
        /** Reads a name saved by an older build, where 대분류/중분류 were two separate sorts. */
        fun fromStoredName(name: String): SortField = when (name) {
            "MAJOR_CATEGORY", "MINOR_CATEGORY" -> CATEGORY
            else -> runCatching { SortField.valueOf(name) }.getOrDefault(END_DATE)
        }
    }
}
