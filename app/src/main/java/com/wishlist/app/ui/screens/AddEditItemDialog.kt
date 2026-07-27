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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wishlist.app.data.WishlistItem
import com.wishlist.app.ui.components.DateTimeField
import com.wishlist.app.ui.WishlistViewModel

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
    var majorCategory by remember { mutableStateOf(editingItem?.majorCategory.orEmpty()) }
    var minorCategory by remember { mutableStateOf(editingItem?.minorCategory.orEmpty()) }
    var startedAt by remember { mutableStateOf(editingItem?.startedAt ?: System.currentTimeMillis()) }
    var completed by remember { mutableStateOf(editingItem?.isCompleted ?: false) }
    var completedAt by remember { mutableStateOf(editingItem?.completedAt ?: System.currentTimeMillis()) }

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

                DateTimeField(
                    label = "고민을 시작한 시간",
                    epochMillis = startedAt,
                    onValueChange = { startedAt = it },
                )
                Spacer(Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = completed, onCheckedChange = { completed = it })
                    Text("완료됨")
                }
                if (completed) {
                    DateTimeField(
                        label = "완료일자",
                        epochMillis = completedAt,
                        onValueChange = { completedAt = it },
                        trailingContent = {
                            TextButton(onClick = { completed = false }) { Text("진행 중으로 변경") }
                        },
                    )
                }
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
                                    majorCategory = majorCategory.trim().ifBlank { null },
                                    minorCategory = minorCategory.trim().ifBlank { null },
                                    startedAt = startedAt,
                                    completedAt = if (completed) completedAt else null,
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
