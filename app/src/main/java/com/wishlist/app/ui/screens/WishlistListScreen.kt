package com.wishlist.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.wishlist.app.data.SortField
import com.wishlist.app.data.StatusFilter
import com.wishlist.app.data.WishlistItem
import com.wishlist.app.repository.CategoryGroup
import com.wishlist.app.repository.WishlistUiState
import com.wishlist.app.ui.components.CategoryGroupHeader
import com.wishlist.app.ui.components.WishlistItemRow
import com.wishlist.app.ui.components.dragHandle
import com.wishlist.app.ui.components.rememberDragDropState

/** One LazyColumn entry. Headers and items share a flat index so drag positions are unambiguous. */
private sealed interface ListRow {
    val categoryKey: String

    data class Header(val group: CategoryGroup) : ListRow {
        override val categoryKey: String get() = group.categoryKey
    }

    data class Entry(override val categoryKey: String, val item: WishlistItem) : ListRow
}

private fun buildRows(groups: List<CategoryGroup>): List<ListRow> = buildList {
    groups.forEach { group ->
        add(ListRow.Header(group))
        group.items.forEach { add(ListRow.Entry(group.categoryKey, it)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistListScreen(
    uiState: WishlistUiState,
    onStatusFilterChange: (StatusFilter) -> Unit,
    onSortFieldSelected: (CategoryGroup, SortField) -> Unit,
    onToggleDirection: (CategoryGroup) -> Unit,
    onToggleCompleted: (WishlistItem) -> Unit,
    onToggleSubItem: (WishlistItem, Int) -> Unit,
    onMoveSubItem: (WishlistItem, Int, Int) -> Unit,
    onReorder: (categoryKey: String, orderedIds: List<String>) -> Unit,
    onItemClick: (WishlistItem) -> Unit,
    onAddClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    // Rows are held locally so a drag rearranges them instantly; incoming Firestore updates are
    // only adopted while nothing is being dragged, so they can't yank a row out from under a finger.
    var rows by remember { mutableStateOf(buildRows(uiState.groups)) }
    val listState = rememberLazyListState()

    val dragDropState = rememberDragDropState(
        lazyListState = listState,
        canDrag = { index -> rows.getOrNull(index) is ListRow.Entry },
        onMove = { from, to ->
            val moved = rows.getOrNull(from) as? ListRow.Entry ?: return@rememberDragDropState
            val target = rows.getOrNull(to) as? ListRow.Entry ?: return@rememberDragDropState
            // Items belong to their category; dragging across a header would silently recategorize.
            if (moved.categoryKey != target.categoryKey) return@rememberDragDropState
            rows = rows.toMutableList().apply { add(to, removeAt(from)) }
        },
        onDragFinished = { index ->
            val dragged = rows.getOrNull(index) as? ListRow.Entry ?: return@rememberDragDropState
            val orderedIds = rows.filterIsInstance<ListRow.Entry>()
                .filter { it.categoryKey == dragged.categoryKey }
                .map { it.item.id }
            onReorder(dragged.categoryKey, orderedIds)
        },
    )

    LaunchedEffect(uiState.groups, dragDropState.draggingItemIndex) {
        if (dragDropState.draggingItemIndex == null) {
            rows = buildRows(uiState.groups)
        }
    }

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
            // Labeled rather than icon-only: a bare "+" circle was easy to miss.
            ExtendedFloatingActionButton(
                onClick = onAddClick,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("할 일 추가") },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = uiState.statusFilter == StatusFilter.ALL,
                    onClick = { onStatusFilterChange(StatusFilter.ALL) },
                    label = { Text("전체") },
                )
                FilterChip(
                    selected = uiState.statusFilter == StatusFilter.IN_PROGRESS,
                    onClick = { onStatusFilterChange(StatusFilter.IN_PROGRESS) },
                    label = { Text("진행 중") },
                )
                FilterChip(
                    selected = uiState.statusFilter == StatusFilter.COMPLETED,
                    onClick = { onStatusFilterChange(StatusFilter.COMPLETED) },
                    label = { Text("완료") },
                )
            }

            if (rows.isEmpty() && !uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "아직 등록된 항목이 없습니다.",
                        modifier = Modifier.padding(24.dp),
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 88.dp),
                ) {
                    itemsIndexed(
                        items = rows,
                        key = { _, row ->
                            when (row) {
                                is ListRow.Header -> "header:${row.categoryKey}"
                                is ListRow.Entry -> row.item.id
                            }
                        },
                    ) { index, row ->
                        val dragging = index == dragDropState.draggingItemIndex
                        val rowModifier = Modifier.graphicsLayer {
                            if (dragging) {
                                translationY = dragDropState.draggingItemOffset
                                // Lift the dragged row above its neighbours while it moves.
                                shadowElevation = 8f
                            }
                        }
                        when (row) {
                            is ListRow.Header -> CategoryGroupHeader(
                                title = categoryDisplayName(row.group),
                                sortField = row.group.sortField,
                                ascending = row.group.ascending,
                                onSortFieldSelected = { field -> onSortFieldSelected(row.group, field) },
                                onToggleDirection = { onToggleDirection(row.group) },
                            )

                            is ListRow.Entry -> Box(modifier = rowModifier) {
                                WishlistItemRow(
                                    item = row.item,
                                    dragHandleModifier = Modifier.dragHandle(dragDropState, index),
                                    onToggleCompleted = { onToggleCompleted(row.item) },
                                    onToggleSubItem = { subIndex -> onToggleSubItem(row.item, subIndex) },
                                    onMoveSubItem = { from, to -> onMoveSubItem(row.item, from, to) },
                                    onClick = { onItemClick(row.item) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun categoryDisplayName(group: CategoryGroup): String {
    if (group.categoryKey == WishlistItem.UNCATEGORIZED_KEY) return "미분류"
    return listOfNotNull(group.majorCategory, group.minorCategory).joinToString(" · ")
}
