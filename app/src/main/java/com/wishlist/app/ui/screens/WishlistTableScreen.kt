package com.wishlist.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wishlist.app.data.SortField
import com.wishlist.app.repository.TableRow
import com.wishlist.app.repository.WishlistUiState
import com.wishlist.app.ui.theme.categoryColor
import com.wishlist.app.ui.theme.monthColor
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
    val major = 52.dp * scale
    val minor = 52.dp * scale
    val title = 100.dp * scale
    val subItem = 124.dp * scale
    val endDate = 80.dp * scale
    val remaining = 56.dp * scale
    val priority = 40.dp * scale
    val done = 44.dp * scale

    /** Breathing room inside a cell; the grid is dense on purpose. */
    val cellPadding = 4.dp * scale
}

/** Sum of the base column widths — what the grid needs before any stretching. */
private val BASE_TABLE_WIDTH = 548.dp

/** Text grows with the columns, but far more slowly: past this it just wastes the space again. */
private const val MAX_TEXT_SCALE = 1.25f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistTableScreen(
    uiState: WishlistUiState,
    onHideCompletedChange: (Boolean) -> Unit,
    onSortSelected: (SortField) -> Unit,
    onToggleRowDone: (TableRow) -> Unit,
    onRowClick: (TableRow) -> Unit,
    onAddClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    // One shared scroll state: the header must slide with the rows or the columns stop matching.
    val horizontalScroll = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wishlist") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Filled.Settings, contentDescription = "설정")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddClick,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("할 일 추가") },
            )
        },
    ) { padding ->
        BoxWithConstraints(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Stretch to fill a tablet, never squeeze below the phone-sized grid.
            val cols = Columns((maxWidth / BASE_TABLE_WIDTH).coerceAtLeast(1f))
            val cellStyle = MaterialTheme.typography.bodySmall.let { style ->
                style.copy(fontSize = style.fontSize * cols.scale.coerceAtMost(MAX_TEXT_SCALE))
            }
            // Sorting by 완료예정일 colors each line by its month instead of by (대/중분류): the point
            // of that view is when things are due, so the month is what the eye should group by.
            val colorByMonth = uiState.sortField == SortField.END_DATE
            // Read from the theme in effect rather than the system setting, since the theme can be
            // overridden per device in 설정. Tints need both a different color and a heavier alpha
            // on a dark surface to be told apart at all.
            val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

            Column(modifier = Modifier.fillMaxSize()) {
                // Sync trouble is shown and worked through, not crashed on: the table still works
                // from Firestore's local cache while this is up.
                uiState.syncError?.let { message ->
                    Surface(color = MaterialTheme.colorScheme.errorContainer) {
                        SelectionContainer {
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Checkbox(checked = uiState.hideCompleted, onCheckedChange = onHideCompletedChange)
                    Text("완료항목 숨기기", style = MaterialTheme.typography.bodySmall)
                }

                Column(modifier = Modifier.horizontalScroll(horizontalScroll)) {
                    HeaderRow(
                        cols = cols,
                        sortField = uiState.sortField,
                        ascending = uiState.ascending,
                        onSortSelected = onSortSelected,
                    )
                    HorizontalDivider()

                    if (uiState.rows.isEmpty() && !uiState.isLoading) {
                        Text(
                            text = "아직 등록된 항목이 없습니다.",
                            modifier = Modifier.padding(24.dp),
                        )
                    } else {
                        // No fillMaxSize/fillMaxWidth below this point: horizontalScroll hands
                        // children unbounded width, so the grid sizes itself from the column widths.
                        LazyColumn {
                            items(uiState.rows, key = { it.rowKey }) { row ->
                                DataRow(
                                    row = row,
                                    cols = cols,
                                    textStyle = cellStyle,
                                    dark = dark,
                                    tint = if (colorByMonth) {
                                        monthColor(row.effectiveEndDate, dark)
                                    } else {
                                        categoryColor(
                                            row.item.majorCategory,
                                            row.item.minorCategory,
                                            uiState.categoryColors,
                                        )
                                    },
                                    // The 대분류/중분류 band is a category cue; it has no meaning when
                                    // the color stands for a month.
                                    bandCategoryCells = !colorByMonth,
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
        HeaderCell("대/중분류", cols.major + cols.minor, cols, SortField.CATEGORY, sortField, ascending, onSortSelected)
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (isActive) {
            Icon(
                imageVector = if (ascending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                contentDescription = if (ascending) "오름차순" else "내림차순",
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
    /** What this line's color means — its (대/중분류), or the month it's due in. */
    tint: Color?,
    bandCategoryCells: Boolean,
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

    // Strong enough to tell two tints apart at a glance while leaving the text readable. A dark
    // surface swallows a translucent color, so it gets more alpha than the light one.
    val rowAlpha = if (dark) 0.32f else 0.24f
    val headAlpha = if (dark) 0.46f else 0.38f
    val background = when {
        tint != null -> tint.copy(alpha = if (isBlockHead) headAlpha else rowAlpha)
        isBlockHead -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        else -> Color.Transparent
    }
    // The 대분류/중분류 columns themselves get the stronger tint, so a category color reads as a band
    // down the left of the table rather than a wash over every row.
    val categoryCellBackground = if (bandCategoryCells) {
        tint?.copy(alpha = if (dark) 0.6f else 0.55f) ?: Color.Transparent
    } else {
        Color.Transparent
    }

    Row(
        // IntrinsicSize.Min gives the row a bounded height inside the LazyColumn, which is what lets
        // the tinted category cells fill it instead of collapsing to the height of their text.
        modifier = Modifier
            .height(IntrinsicSize.Min)
            .background(background)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
            background = categoryCellBackground,
        )
        Cell(
            text = if (isChild) "" else row.item.minorCategory.orEmpty(),
            width = cols.minor,
            cols = cols,
            textStyle = textStyle,
            strike = strike,
            onClick = onClick,
            weight = weight,
            background = categoryCellBackground,
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
            textStyle = textStyle,
            strike = strike,
            onClick = onClick,
        )
        Cell(
            text = if (row.isDone) "완료" else formatRemainingDays(row.effectiveEndDate),
            width = cols.remaining,
            cols = cols,
            textStyle = textStyle,
            strike = strike,
            onClick = onClick,
            color = MaterialTheme.colorScheme.primary,
        )
        Cell(
            text = if (isChild) "" else "${row.item.priority}",
            width = cols.priority,
            cols = cols,
            textStyle = textStyle,
            strike = strike,
            onClick = onClick,
        )
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
    background: Color = Color.Transparent,
) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(background)
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
