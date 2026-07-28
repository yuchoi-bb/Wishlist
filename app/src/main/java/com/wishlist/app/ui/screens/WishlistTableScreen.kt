package com.wishlist.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wishlist.app.data.SortField
import com.wishlist.app.repository.TableRow
import com.wishlist.app.repository.WishlistUiState
import com.wishlist.app.util.formatDate
import com.wishlist.app.util.formatRemainingDays

/** Column widths, shared by the header and every row so the grid lines up. */
private object Col {
    val major = 84.dp
    val minor = 84.dp
    val title = 128.dp
    val subItem = 140.dp
    val endDate = 96.dp
    val remaining = 64.dp
    val priority = 44.dp
    val done = 44.dp
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistTableScreen(
    uiState: WishlistUiState,
    onShowCompletedChange: (Boolean) -> Unit,
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
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Checkbox(checked = uiState.showCompleted, onCheckedChange = onShowCompletedChange)
                Text("완료된 항목 보기", style = MaterialTheme.typography.bodySmall)
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
        HeaderCell("대분류", Col.major, SortField.MAJOR_CATEGORY, sortField, ascending, onSortSelected)
        HeaderCell("중분류", Col.minor, SortField.MINOR_CATEGORY, sortField, ascending, onSortSelected)
        HeaderCell("할 일", Col.title, SortField.TITLE, sortField, ascending, onSortSelected)
        HeaderCell("세부항목", Col.subItem, SortField.SUB_ITEM, sortField, ascending, onSortSelected)
        HeaderCell("종료일", Col.endDate, SortField.END_DATE, sortField, ascending, onSortSelected)
        HeaderCell("남은날짜", Col.remaining, null, sortField, ascending, onSortSelected)
        HeaderCell("순위", Col.priority, SortField.PRIORITY, sortField, ascending, onSortSelected)
        HeaderCell("완료", Col.done, null, sortField, ascending, onSortSelected)
    }
}

/** Tapping a sortable header sorts by it; tapping the active one flips direction. */
@Composable
private fun HeaderCell(
    label: String,
    width: androidx.compose.ui.unit.Dp,
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
            .padding(horizontal = 6.dp),
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
    onToggleDone: () -> Unit,
    onClick: () -> Unit,
) {
    val strike = if (row.isDone) TextDecoration.LineThrough else null
    Row(
        modifier = Modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Everything except the done checkbox opens the editor.
        Cell(row.item.majorCategory.orEmpty(), Col.major, strike, onClick)
        Cell(row.item.minorCategory.orEmpty(), Col.minor, strike, onClick)
        Cell(row.item.title, Col.title, strike, onClick)
        Cell(row.subItem?.title ?: "-", Col.subItem, strike, onClick)
        Cell(row.effectiveEndDate?.let { formatDate(it) } ?: "-", Col.endDate, strike, onClick)
        Cell(
            text = if (row.isDone) "완료" else formatRemainingDays(row.effectiveEndDate),
            width = Col.remaining,
            strike = strike,
            onClick = onClick,
            color = MaterialTheme.colorScheme.primary,
        )
        Cell("${row.item.priority}", Col.priority, strike, onClick)
        Box(modifier = Modifier.width(Col.done), contentAlignment = Alignment.Center) {
            Checkbox(checked = row.isDone, onCheckedChange = { onToggleDone() })
        }
    }
}

@Composable
private fun Cell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    strike: TextDecoration?,
    onClick: () -> Unit,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        textDecoration = strike,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(width).clickable(onClick = onClick).padding(horizontal = 6.dp),
    )
}
