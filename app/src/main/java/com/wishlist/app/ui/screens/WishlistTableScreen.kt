package com.wishlist.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wishlist.app.data.CategoryColorPref
import com.wishlist.app.data.SortField
import com.wishlist.app.repository.TableRow
import com.wishlist.app.repository.WishlistUiState
import com.wishlist.app.ui.theme.categoryColor
import com.wishlist.app.util.formatDate
import com.wishlist.app.util.formatRemainingDays

/**
 * Column widths, shared by the header and every row so the grid lines up. Kept as tight as the
 * content allows — every column that isn't needed pushes the rest off the side of a phone.
 */
private object Col {
    val major = 52.dp
    val minor = 52.dp
    val title = 100.dp
    val subItem = 124.dp
    val endDate = 80.dp
    val remaining = 56.dp
    val priority = 40.dp
    val done = 44.dp
}

/** Breathing room inside a cell; the grid is dense on purpose. */
private val CELL_PADDING = 4.dp

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
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Sync trouble is shown and worked through, not crashed on: the table still works from
            // Firestore's local cache while this is up.
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
                    // No fillMaxSize/fillMaxWidth below this point: horizontalScroll hands children
                    // unbounded width, so the grid sizes itself from the fixed column widths.
                    LazyColumn {
                        items(uiState.rows, key = { it.rowKey }) { row ->
                            DataRow(
                                row = row,
                                categoryColors = uiState.categoryColors,
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
        HeaderCell("대/중분류", Col.major + Col.minor, SortField.CATEGORY, sortField, ascending, onSortSelected)
        HeaderCell("할 일", Col.title, SortField.TITLE, sortField, ascending, onSortSelected)
        HeaderCell("세부항목", Col.subItem, SortField.SUB_ITEM, sortField, ascending, onSortSelected)
        HeaderCell("예정일", Col.endDate, SortField.END_DATE, sortField, ascending, onSortSelected)
        HeaderCell("남은날짜", Col.remaining, null, sortField, ascending, onSortSelected)
        HeaderCell("순위", Col.priority, SortField.PRIORITY, sortField, ascending, onSortSelected)
        HeaderCell("완료", Col.done, null, sortField, ascending, onSortSelected)
    }
}

/** Tapping a sortable header sorts by it; tapping the active one flips direction. */
@Composable
private fun HeaderCell(
    label: String,
    width: Dp,
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
            .padding(horizontal = CELL_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (isActive) {
            Icon(
                imageVector = if (ascending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                contentDescription = if (ascending) "오름차순" else "내림차순",
                modifier = Modifier.width(14.dp),
            )
        }
    }
}

@Composable
private fun DataRow(
    row: TableRow,
    categoryColors: List<CategoryColorPref>,
    onToggleDone: () -> Unit,
    onClick: () -> Unit,
) {
    val strike = if (row.isDone) TextDecoration.LineThrough else null
    // An 항목 line reads as the head of its block; a 세부항목 line under it is indented and leaves the
    // 항목 columns empty, so the two never look like the same kind of thing.
    val isChild = row.grouped && !row.isItemRow
    val isBlockHead = row.isItemRow && row.grouped
    val weight = if (isBlockHead) FontWeight.SemiBold else FontWeight.Normal

    // Every line of a (대/중분류) is tinted with that category's color, so the same category is
    // recognizable wherever the sort puts it. Alpha keeps the text readable in both themes.
    val tint = categoryColor(row.item.majorCategory, row.item.minorCategory, categoryColors)
    val background = when {
        tint != null -> tint.copy(alpha = if (isBlockHead) 0.24f else 0.12f)
        isBlockHead -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        else -> Color.Transparent
    }
    // The 대분류/중분류 columns themselves get the stronger tint, so the color reads as a band down
    // the left of the table rather than a wash over every row.
    val categoryCellBackground = tint?.copy(alpha = 0.4f) ?: Color.Transparent

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
            width = Col.major,
            strike = strike,
            onClick = onClick,
            weight = weight,
            background = categoryCellBackground,
        )
        Cell(
            text = if (isChild) "" else row.item.minorCategory.orEmpty(),
            width = Col.minor,
            strike = strike,
            onClick = onClick,
            weight = weight,
            background = categoryCellBackground,
        )
        Cell(if (isChild) "" else row.item.title, Col.title, strike, onClick, weight = weight)
        Cell(subItemLabel(row), Col.subItem, strike, onClick, indent = if (isChild) 8.dp else 0.dp)
        Cell(row.effectiveEndDate?.let { formatDate(it) } ?: "-", Col.endDate, strike, onClick)
        Cell(
            text = if (row.isDone) "완료" else formatRemainingDays(row.effectiveEndDate),
            width = Col.remaining,
            strike = strike,
            onClick = onClick,
            color = MaterialTheme.colorScheme.primary,
        )
        Cell(if (isChild) "" else "${row.item.priority}", Col.priority, strike, onClick)
        Box(modifier = Modifier.width(Col.done), contentAlignment = Alignment.Center) {
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
            .padding(start = CELL_PADDING + indent, end = CELL_PADDING),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            fontWeight = weight,
            textDecoration = strike,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
