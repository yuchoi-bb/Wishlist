package com.wishlist.app.ui.screens

import androidx.compose.ui.graphics.Color
import com.wishlist.app.data.CategoryColorPref
import com.wishlist.app.data.SortField
import com.wishlist.app.data.WishlistItem
import com.wishlist.app.repository.TableRow
import com.wishlist.app.ui.theme.categoryColor
import com.wishlist.app.ui.theme.monthColor
import com.wishlist.app.util.formatYearMonth

/** A run of lines that belong together under the current sort, with the color that says so. */
data class RowSection(
    val key: String,
    /** Null when the sort gives no meaningful grouping — the rows are then shown without a band. */
    val label: String?,
    val tint: Color?,
    val rows: List<TableRow>,
)

/**
 * Splits sorted lines into bands the eye can use. Sorting by 예정일 groups by month — the whole point
 * of that view is when things are due — and sorting by (대/중분류) groups by category. Every other
 * sort is a flat ranking with nothing to band, so it comes back as one unlabelled run.
 *
 * Grouping is on RUNS, not on values: the rows arrive sorted, so a run is a group, and a band never
 * claims to hold rows that the sort put somewhere else.
 */
fun List<TableRow>.sectionsFor(
    sortField: SortField,
    categoryColors: List<CategoryColorPref>,
    dark: Boolean,
): List<RowSection> {
    if (isEmpty()) return emptyList()

    val keyOf: (TableRow) -> String = when (sortField) {
        SortField.END_DATE -> { row -> row.effectiveEndDate?.let { formatYearMonth(it) } ?: NO_DATE }
        SortField.CATEGORY -> { row -> row.item.categoryKey }
        else -> return listOf(RowSection(key = "all", label = null, tint = null, rows = this))
    }

    val sections = mutableListOf<RowSection>()
    var currentKey = keyOf(first())
    var current = mutableListOf<TableRow>()

    fun flush() {
        if (current.isEmpty()) return
        sections += RowSection(
            key = currentKey,
            label = labelFor(sortField, currentKey, current.first()),
            tint = tintFor(sortField, current.first(), categoryColors, dark),
            rows = current,
        )
    }

    forEach { row ->
        val key = keyOf(row)
        if (key != currentKey) {
            flush()
            currentKey = key
            current = mutableListOf()
        }
        current += row
    }
    flush()
    return sections
}

/** The color a section's band and its rows' rails carry. */
fun tintFor(
    sortField: SortField,
    row: TableRow,
    categoryColors: List<CategoryColorPref>,
    dark: Boolean,
): Color? = when (sortField) {
    SortField.END_DATE -> monthColor(row.effectiveEndDate, dark)
    else -> categoryColor(row.item.majorCategory, row.item.minorCategory, categoryColors)
}

private fun labelFor(sortField: SortField, key: String, sample: TableRow): String = when {
    sortField == SortField.END_DATE && key == NO_DATE -> "예정일 없음"
    sortField == SortField.END_DATE -> key
    key == WishlistItem.UNCATEGORIZED_KEY -> "분류 없음"
    else -> listOfNotNull(
        sample.item.majorCategory?.takeIf { it.isNotBlank() },
        sample.item.minorCategory?.takeIf { it.isNotBlank() },
    ).joinToString(" › ")
}

private const val NO_DATE = "__no_date__"
