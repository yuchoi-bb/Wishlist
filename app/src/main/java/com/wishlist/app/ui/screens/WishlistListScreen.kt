package com.wishlist.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wishlist.app.data.StatusFilter
import com.wishlist.app.repository.CategoryGroup
import com.wishlist.app.repository.WishlistUiState
import com.wishlist.app.ui.components.CategoryGroupHeader
import com.wishlist.app.ui.components.WishlistItemRow

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WishlistListScreen(
    uiState: WishlistUiState,
    onSearchQueryChange: (String) -> Unit,
    onStatusFilterChange: (StatusFilter) -> Unit,
    onSortFieldSelected: (CategoryGroup, com.wishlist.app.data.SortField) -> Unit,
    onToggleDirection: (CategoryGroup) -> Unit,
    onToggleCompleted: (com.wishlist.app.data.WishlistItem) -> Unit,
    onItemClick: (com.wishlist.app.data.WishlistItem) -> Unit,
    onAddClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
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
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Filled.Add, contentDescription = "추가")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                label = { Text("할 일 검색") },
                singleLine = true,
            )

            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
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

            if (uiState.groups.isEmpty() && !uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "아직 등록된 항목이 없습니다.",
                        modifier = Modifier.padding(24.dp),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 88.dp),
                ) {
                    uiState.groups.forEach { group ->
                        stickyHeader(key = group.categoryKey) {
                            CategoryGroupHeader(
                                title = categoryDisplayName(group),
                                sortField = group.sortField,
                                ascending = group.ascending,
                                onSortFieldSelected = { field -> onSortFieldSelected(group, field) },
                                onToggleDirection = { onToggleDirection(group) },
                            )
                        }
                        items(group.items, key = { it.id }) { item ->
                            WishlistItemRow(
                                item = item,
                                onToggleCompleted = { onToggleCompleted(item) },
                                onClick = { onItemClick(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun categoryDisplayName(group: CategoryGroup): String {
    if (group.categoryKey == com.wishlist.app.data.WishlistItem.UNCATEGORIZED_KEY) return "미분류"
    return listOfNotNull(group.majorCategory, group.minorCategory).joinToString(" · ")
}
