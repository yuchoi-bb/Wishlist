package com.wishlist.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wishlist.app.data.SortField
import com.wishlist.app.repository.TableRow
import com.wishlist.app.repository.WishlistUiState
import com.wishlist.app.ui.theme.Serif
import com.wishlist.app.ui.theme.dueColors
import com.wishlist.app.ui.theme.dueStateOf
import com.wishlist.app.ui.theme.priorityColors
import com.wishlist.app.util.formatDate
import com.wishlist.app.util.formatRemainingDays

/**
 * Column widths, shared by the header and every row so the grid lines up. The base widths are as
 * tight as the content allows, because on a phone every extra dp pushes a column off the side.
 *
 * [scale] stretches them to fill a wider screen: on a tablet the base grid leaves most of the width
 * empty, so the columns grow to take it instead. A phone can't fit the base grid to begin with, so
 * the caller clamps the scale to 1 there and nothing about the phone layout changes.
 */
private class Columns(val scale: Float) {
    /** The color rail is carved out of the first column, so the grid keeps its total width. */
    val rail = 3.dp
    val major = 52.dp * scale - rail
    val minor = 52.dp * scale
    val title = 100.dp * scale
    val subItem = 124.dp * scale
    val endDate = 80.dp * scale
    val remaining = 56.dp * scale
    val priority = 48.dp * scale
    val done = 44.dp * scale

    /** Breathing room inside a cell; the grid is dense on purpose. */
    val cellPadding = 4.dp * scale

    val total: Dp = rail + major + minor + title + subItem + endDate + remaining + priority + done
}

/** Sum of the base column widths — what the grid needs before any stretching. */
private val BASE_TABLE_WIDTH = 556.dp

/** Text grows with the columns, but far more slowly: past this it just wastes the space again. */
private const val MAX_TEXT_SCALE = 1.25f

/**
 * The 표 view: one line per 세부항목 across eight columns, scrolled sideways under a header whose
 * columns sort when tapped. Runs of lines that share a month (under a 예정일 sort) or a (대/중분류)
 * carry a band above them, and every line carries that group's color as a rail down its left edge —
 * a rail reads at the same strength in both themes, which a wash over the row does not.
 */
@Composable
fun WishlistTableScreen(
    uiState: WishlistUiState,
    dark: Boolean,
    onSortSelected: (SortField) -> Unit,
    onToggleRowDone: (TableRow) -> Unit,
    onRowClick: (TableRow) -> Unit,
) {
    // One shared scroll state: the header must slide with the rows or the columns stop matching.
    val horizontalScroll = rememberScrollState()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Stretch to fill a tablet, never squeeze below the phone-sized grid.
        val cols = Columns((maxWidth / BASE_TABLE_WIDTH).coerceAtLeast(1f))
        val cellStyle = MaterialTheme.typography.bodySmall.let { style ->
            style.copy(fontSize = style.fontSize * cols.scale.coerceAtMost(MAX_TEXT_SCALE))
        }
        val sections = uiState.rows.sectionsFor(uiState.sortField, uiState.categoryColors, dark)

        Column(modifier = Modifier.horizontalScroll(horizontalScroll)) {
            HeaderRow(
                cols = cols,
                sortField = uiState.sortField,
                ascending = uiState.ascending,
                onSortSelected = onSortSelected,
            )
            HorizontalDivider()

            if (uiState.rows.isEmpty() && !uiState.isLoading) {
                Text(text = "아직 등록된 항목이 없습니다.", modifier = Modifier.padding(24.dp))
            } else {
                // No fillMaxSize/fillMaxWidth below this point: horizontalScroll hands children
                // unbounded width, so the grid sizes itself from the column widths.
                LazyColumn {
                    sections.forEach { section ->
                        if (section.label != null) {
                            item(key = "section:${section.key}") {
                                SectionHeader(
                                    label = section.label,
                                    count = section.rows.size,
                                    tint = section.tint,
                                    modifier = Modifier.width(cols.total),
                                )
                                HorizontalDivider()
                            }
                        }
                        items(
                            count = section.rows.size,
                            key = { index -> section.rows[index].rowKey },
                        ) { index ->
                            val row = section.rows[index]
                            DataRow(
                                row = row,
                                cols = cols,
                                textStyle = cellStyle,
                                dark = dark,
                                // Unbanded sorts still get a rail, from the row's own category.
                                tint = section.tint
                                    ?: tintFor(uiState.sortField, row, uiState.categoryColors, dark),
                                onToggleDone = { onToggleRowDone(row) },
                                onClick = { onRowClick(row) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderRow(
    cols: Columns,
    sortField: SortField,
    ascending: Boolean,
    onSortSelected: (SortField) -> Unit,
) {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 대분류 and 중분류 sort as a single unit, so they share one header spanning both columns.
        HeaderCell("대/중분류", cols.rail + cols.major + cols.minor, cols, SortField.CATEGORY, sortField, ascending, onSortSelected)
        HeaderCell("할 일", cols.title, cols, SortField.TITLE, sortField, ascending, onSortSelected)
        HeaderCell("세부항목", cols.subItem, cols, SortField.SUB_ITEM, sortField, ascending, onSortSelected)
        HeaderCell("예정일", cols.endDate, cols, SortField.END_DATE, sortField, ascending, onSortSelected)
        HeaderCell("남은날짜", cols.remaining, cols, null, sortField, ascending, onSortSelected)
        HeaderCell("순위", cols.priority, cols, SortField.PRIORITY, sortField, ascending, onSortSelected)
        HeaderCell("완료", cols.done, cols, null, sortField, ascending, onSortSelected)
    }
}

/** Tapping a sortable header sorts by it; tapping the active one flips direction. */
@Composable
private fun HeaderCell(
    label: String,
    width: Dp,
    cols: Columns,
    field: SortField?,
    activeField: SortField,
    ascending: Boolean,
    onSortSelected: (SortField) -> Unit,
) {
    val isActive = field != null && field == activeField
    Row(
        modifier = Modifier
            .width(width)
            .then(if (field != null) Modifier.clickable { onSortSelected(field) } else Modifier)
            .padding(horizontal = cols.cellPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val labelStyle = MaterialTheme.typography.labelMedium.let { style ->
            style.copy(fontSize = style.fontSize * cols.scale.coerceAtMost(MAX_TEXT_SCALE))
        }
        Text(
            text = label,
            style = labelStyle,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            color = if (isActive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (isActive) {
            Icon(
                imageVector = if (ascending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                contentDescription = if (ascending) "오름차순" else "내림차순",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(14.dp * cols.scale.coerceAtMost(MAX_TEXT_SCALE)),
            )
        }
    }
}

@Composable
private fun DataRow(
    row: TableRow,
    cols: Columns,
    textStyle: TextStyle,
    /** The group's color — its (대/중분류), or the month it's due in. */
    tint: Color?,
    dark: Boolean,
    onToggleDone: () -> Unit,
    onClick: () -> Unit,
) {
    val strike = if (row.isDone) TextDecoration.LineThrough else null
    // An 항목 line reads as the head of its block; a 세부항목 line under it is indented and leaves the
    // 항목 columns empty, so the two never look like the same kind of thing.
    val isChild = row.grouped && !row.isItemRow
    val isBlockHead = row.isItemRow && row.grouped
    val weight = if (isBlockHead) FontWeight.SemiBold else FontWeight.Normal
    // Light enough to read text over — the rail carries the color, this only ties the row to it.
    val wash = when {
        tint != null && isBlockHead -> tint.copy(alpha = if (dark) 0.22f else 0.16f)
        tint != null -> tint.copy(alpha = if (dark) 0.10f else 0.07f)
        else -> Color.Transparent
    }

    Row(
        // IntrinsicSize.Min gives the row a bounded height inside the LazyColumn, which is what lets
        // the rail fill it instead of collapsing to the height of the text.
        modifier = Modifier
            .height(IntrinsicSize.Min)
            .background(wash)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(cols.rail)
                .fillMaxHeight()
                .background(tint ?: Color.Transparent),
        )
        // Everything except the done checkbox opens the editor, which always shows the whole 항목
        // together with its 세부항목 — tapping a child line edits its parent.
        Cell(
            text = if (isChild) "" else row.item.majorCategory.orEmpty(),
            width = cols.major,
            cols = cols,
            textStyle = textStyle,
            strike = strike,
            onClick = onClick,
            weight = weight,
        )
        Cell(
            text = if (isChild) "" else row.item.minorCategory.orEmpty(),
            width = cols.minor,
            cols = cols,
            textStyle = textStyle,
            strike = strike,
            onClick = onClick,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Cell(
            text = if (isChild) "" else row.item.title,
            width = cols.title,
            cols = cols,
            textStyle = textStyle,
            strike = strike,
            onClick = onClick,
            weight = weight,
        )
        Cell(
            text = subItemLabel(row),
            width = cols.subItem,
            cols = cols,
            textStyle = textStyle,
            strike = strike,
            onClick = onClick,
            indent = if (isChild) 8.dp * cols.scale else 0.dp,
        )
        Cell(
            text = row.effectiveEndDate?.let { formatDate(it) } ?: "-",
            width = cols.endDate,
            cols = cols,
            // The serif is what keeps a column of dates from reading as machine output.
            textStyle = textStyle.copy(fontFamily = Serif, fontSize = textStyle.fontSize * 1.06f),
            strike = strike,
            onClick = onClick,
        )
        ChipCell(
            width = cols.remaining,
            cols = cols,
            textStyle = textStyle,
            onClick = onClick,
        ) {
            DueChip(endDate = row.effectiveEndDate, isDone = row.isDone, dark = dark, style = textStyle)
        }
        ChipCell(
            width = cols.priority,
            cols = cols,
            textStyle = textStyle,
            onClick = onClick,
        ) {
            if (!isChild) PriorityChip(priority = row.item.priority, dark = dark, style = textStyle)
        }
        Box(modifier = Modifier.width(cols.done), contentAlignment = Alignment.Center) {
            Checkbox(checked = row.isDone, onCheckedChange = { onToggleDone() })
        }
    }
}

/**
 * The 세부항목 column: a child line shows its own name, an 항목 line shows how many of its 세부항목
 * are ticked off, and an item with no 세부항목 shows nothing to count.
 */
private fun subItemLabel(row: TableRow): String = when {
    // The └ only makes sense when the parent 항목 is the line right above.
    !row.isItemRow -> if (row.grouped) "└ ${row.subItem?.title.orEmpty()}" else row.subItem?.title.orEmpty()
    row.item.subItems.isEmpty() -> "-"
    else -> "${row.item.doneSubItemCount}/${row.item.subItems.size} 완료"
}

/** 남은날짜, colored by how close the date is — see [dueStateOf]. */
@Composable
fun DueChip(endDate: Long?, isDone: Boolean, dark: Boolean, style: TextStyle) {
    val colors = dueColors(dueStateOf(endDate, isDone), dark)
    Text(
        text = if (isDone) "완료" else formatRemainingDays(endDate),
        style = style,
        color = colors.content,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier = Modifier
            .background(colors.container, RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

/**
 * 우선순위 in words. "P1" is how a tracker labels a ticket; 높음/보통/여유 is how someone talks about
 * their own week, and it costs one column-width to say it that way.
 */
@Composable
fun PriorityChip(priority: Int, dark: Boolean, style: TextStyle) {
    val colors = priorityColors(priority, dark)
    Text(
        text = priorityLabel(priority),
        style = style,
        color = colors.content,
        fontWeight = if (priority == 1) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = 1,
        modifier = Modifier
            .background(colors.container, RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

fun priorityLabel(priority: Int): String = when (priority) {
    1 -> "높음"
    2 -> "보통"
    else -> "여유"
}

@Composable
private fun ChipCell(
    width: Dp,
    cols: Columns,
    textStyle: TextStyle,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .clickable(onClick = onClick)
            .padding(horizontal = cols.cellPadding),
        contentAlignment = Alignment.CenterStart,
    ) {
        content()
    }
}

@Composable
private fun Cell(
    text: String,
    width: Dp,
    cols: Columns,
    textStyle: TextStyle,
    strike: TextDecoration?,
    onClick: () -> Unit,
    color: Color = MaterialTheme.colorScheme.onSurface,
    weight: FontWeight = FontWeight.Normal,
    indent: Dp = 0.dp,
) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .clickable(onClick = onClick)
            .padding(start = cols.cellPadding + indent, end = cols.cellPadding),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = textStyle,
            color = color,
            fontWeight = weight,
            textDecoration = strike,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
