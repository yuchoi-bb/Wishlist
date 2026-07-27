package com.wishlist.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wishlist.app.data.SubItem
import com.wishlist.app.data.WishlistItem
import com.wishlist.app.ui.WishlistViewModel
import com.wishlist.app.ui.components.DateField
import com.wishlist.app.util.todayStartOfDayMillis

@Composable
fun AddEditItemDialog(
    viewModel: WishlistViewModel,
    majorCategorySuggestions: List<String>,
    editingItem: WishlistItem?,
    onDismiss: () -> Unit,
    onSave: (WishlistItem) -> Unit,
    onDelete: (WishlistItem) -> Unit,
) {
    var title by remember { mutableStateOf(editingItem?.title.orEmpty()) }
    var memo by remember { mutableStateOf(editingItem?.memo.orEmpty()) }
    var majorCategory by remember { mutableStateOf(editingItem?.majorCategory.orEmpty()) }
    var minorCategory by remember { mutableStateOf(editingItem?.minorCategory.orEmpty()) }
    var startedAt by remember { mutableStateOf(editingItem?.startedAt ?: todayStartOfDayMillis()) }
    var completedAt by remember { mutableStateOf(editingItem?.completedAt) }
    val subItems = remember { mutableStateListOf<SubItem>().apply { addAll(editingItem?.subItems.orEmpty()) } }
    var newSubItemTitle by remember { mutableStateOf("") }

    val minorSuggestions by if (majorCategory.isNotBlank()) {
        viewModel.observeMinorCategories(majorCategory).collectAsStateWithLifecycle(initialValue = emptyList())
    } else {
        remember { mutableStateOf(emptyList<String>()) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = if (editingItem == null) "새 할 일" else "할 일 수정",
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("할 일") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    label = { Text("메모") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5,
                )
                Spacer(Modifier.height(16.dp))

                Text("세부항목", style = MaterialTheme.typography.titleSmall)
                subItems.forEachIndexed { index, subItem ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = subItem.done,
                            onCheckedChange = { subItems[index] = subItem.copy(done = it) },
                        )
                        OutlinedTextField(
                            value = subItem.title,
                            onValueChange = { subItems[index] = subItem.copy(title = it) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        IconButton(onClick = { subItems.removeAt(index) }) {
                            Icon(Icons.Filled.Close, contentDescription = "세부항목 삭제")
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = newSubItemTitle,
                        onValueChange = { newSubItemTitle = it },
                        label = { Text("세부항목 추가") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    IconButton(
                        enabled = newSubItemTitle.isNotBlank(),
                        onClick = {
                            subItems.add(SubItem(title = newSubItemTitle.trim()))
                            newSubItemTitle = ""
                        },
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "세부항목 추가")
                    }
                }
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = majorCategory,
                    onValueChange = { majorCategory = it },
                    label = { Text("대분류") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                if (majorCategorySuggestions.isNotEmpty()) {
                    SuggestionRow(majorCategorySuggestions) { majorCategory = it }
                }
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = minorCategory,
                    onValueChange = { minorCategory = it },
                    label = { Text("중분류") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                if (minorSuggestions.isNotEmpty()) {
                    SuggestionRow(minorSuggestions) { minorCategory = it }
                }
                Spacer(Modifier.height(16.dp))

                DateField(
                    label = "시작일",
                    epochMillis = startedAt,
                    onValueChange = { startedAt = it },
                )
                Spacer(Modifier.height(16.dp))

                // Setting a 완료일 is what marks the item complete; clearing it returns it to 진행 중.
                DateField(
                    label = "완료일",
                    epochMillis = completedAt,
                    emptyLabel = "진행 중 (탭하여 완료일 지정)",
                    onValueChange = { completedAt = it },
                    trailingContent = {
                        if (completedAt != null) {
                            TextButton(onClick = { completedAt = null }) { Text("지우기") }
                        }
                    },
                )
                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (editingItem != null) {
                        TextButton(onClick = { onDelete(editingItem) }) { Text("삭제") }
                        Spacer(Modifier.width(8.dp))
                    }
                    TextButton(onClick = onDismiss) { Text("취소") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        enabled = title.isNotBlank(),
                        onClick = {
                            onSave(
                                WishlistItem(
                                    id = editingItem?.id ?: "",
                                    title = title.trim(),
                                    memo = memo.trim().ifBlank { null },
                                    subItems = subItems.filter { it.title.isNotBlank() },
                                    majorCategory = majorCategory.trim().ifBlank { null },
                                    minorCategory = minorCategory.trim().ifBlank { null },
                                    startedAt = startedAt,
                                    completedAt = completedAt,
                                    // A reorder rewrites ranks as 0,1,2…, so a timestamp puts new
                                    // items after anything already arranged by hand.
                                    position = editingItem?.position ?: System.currentTimeMillis(),
                                ),
                            )
                        },
                    ) { Text("저장") }
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(suggestions: List<String>, onSelect: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(suggestions) { suggestion ->
            AssistChip(onClick = { onSelect(suggestion) }, label = { Text(suggestion) })
        }
    }
}
