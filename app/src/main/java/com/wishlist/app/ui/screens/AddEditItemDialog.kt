package com.wishlist.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wishlist.app.data.CategoryColorPref
import com.wishlist.app.data.SubItem
import com.wishlist.app.data.WishlistItem
import com.wishlist.app.share.addToCalendar
import com.wishlist.app.ui.WishlistViewModel
import com.wishlist.app.ui.components.DateField
import com.wishlist.app.ui.components.DateOnlyPicker
import com.wishlist.app.ui.components.MonthEndChips
import com.wishlist.app.ui.theme.CATEGORY_PALETTE
import com.wishlist.app.ui.theme.categoryColor
import com.wishlist.app.ui.theme.paletteIndexFor
import com.wishlist.app.util.formatDate
import com.wishlist.app.util.todayStartOfDayMillis

/**
 * 세부항목 text, two points smaller than the 16sp a text field normally uses: a dialog holds a whole
 * list of them, and the extra room per row is worth more than the size.
 */
private val SUB_ITEM_TEXT_STYLE = TextStyle(fontSize = 14.sp)

/**
 * The 항목 editor, and the only place completion and deletion happen. Field order follows the way an
 * item is actually filled in: 할 일 → 우선순위 → 최종 종료일 → 세부항목 → 분류 → 메모, with 시작일 and
 * 완료 at the end where they're rarely touched.
 */
@Composable
fun AddEditItemDialog(
    viewModel: WishlistViewModel,
    majorCategorySuggestions: List<String>,
    categoryColors: List<CategoryColorPref>,
    editingItem: WishlistItem?,
    onDismiss: () -> Unit,
    onSave: (WishlistItem) -> Unit,
    onDelete: (WishlistItem) -> Unit,
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(editingItem?.title.orEmpty()) }
    var memo by remember { mutableStateOf(editingItem?.memo.orEmpty()) }
    var majorCategory by remember { mutableStateOf(editingItem?.majorCategory.orEmpty()) }
    var minorCategory by remember { mutableStateOf(editingItem?.minorCategory.orEmpty()) }
    var startedAt by remember { mutableStateOf(editingItem?.startedAt ?: todayStartOfDayMillis()) }
    var endDate by remember { mutableStateOf(editingItem?.endDate) }
    var isDone by remember { mutableStateOf(editingItem?.isDone ?: false) }
    var priority by remember { mutableIntStateOf(editingItem?.priority ?: WishlistItem.DEFAULT_PRIORITY) }
    // A 세부항목 with no date of its own starts from its parent 항목's 최종 종료일, so the table has a
    // real 완료예정일 to sort by instead of an empty cell. It stays editable per 세부항목.
    val subItems = remember {
        mutableStateListOf<SubItem>().apply {
            addAll(
                editingItem?.subItems.orEmpty().map { sub ->
                    if (sub.endDate == null) sub.copy(endDate = editingItem?.endDate) else sub
                },
            )
        }
    }
    var newSubItemTitle by remember { mutableStateOf("") }

    /** Setting the 항목's date also fills in any 세부항목 that still has none. */
    val setItemEndDate: (Long?) -> Unit = { value ->
        endDate = value
        if (value != null) {
            // Indices rather than forEach: writing back into the list while iterating it would trip
            // the snapshot list's concurrent-modification check.
            for (index in subItems.indices) {
                val sub = subItems[index]
                if (sub.endDate == null) subItems[index] = sub.copy(endDate = value)
            }
        }
    }

    val minorSuggestions by if (majorCategory.isNotBlank()) {
        viewModel.observeMinorCategories(majorCategory).collectAsStateWithLifecycle(initialValue = emptyList())
    } else {
        remember { mutableStateOf(emptyList<String>()) }
    }

    val isNewItem = editingItem?.id.isNullOrBlank()

    // Fixed once: a new item's rank is its creation time, and re-reading the clock would make the
    // item look edited every time the form is compared against its starting state.
    val position = remember { editingItem?.position?.takeIf { it > 0 } ?: System.currentTimeMillis() }

    fun edited() = WishlistItem(
        id = editingItem?.id ?: "",
        title = title.trim(),
        memo = memo.trim().ifBlank { null },
        subItems = subItems.filter { it.title.isNotBlank() },
        majorCategory = majorCategory.trim().ifBlank { null },
        minorCategory = minorCategory.trim().ifBlank { null },
        startedAt = startedAt,
        endDate = endDate,
        isDone = isDone,
        priority = priority,
        position = position,
    )

    // The form as it stood when it opened. Comparing against this — rather than against the stored
    // item — means the 세부항목 date prefill above doesn't count as an edit the user made.
    val opened = remember { edited() }
    var askBeforeLeaving by remember { mutableStateOf(false) }

    /** Back, 취소 and tapping outside all come through here so nothing is lost silently. */
    val requestDismiss = {
        if (edited() == opened) onDismiss() else askBeforeLeaving = true
    }

    Dialog(onDismissRequest = requestDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    // A draft from another app's share arrives as an item with no id yet, so it's
                    // the id — not the presence of an item — that says whether this is new.
                    text = if (isNewItem) "새 할 일" else "할 일 수정",
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

                Text("우선순위", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WishlistItem.PRIORITIES.forEach { value ->
                        FilterChip(
                            selected = priority == value,
                            onClick = { priority = value },
                            label = { Text("$value") },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                // 최종 종료일 is the date the whole item is meant to be finished by, which is what
                // 남은날짜 counts down to. Whether it's actually finished is the 완료 checkbox below.
                DateField(
                    label = "최종 종료일 (목표일)",
                    epochMillis = endDate,
                    emptyLabel = "지정 안 됨 (탭하여 선택)",
                    onValueChange = setItemEndDate,
                    trailingContent = {
                        if (endDate != null) {
                            TextButton(onClick = { endDate = null }) { Text("지우기") }
                            // Opens the calendar app's new-event screen filled in; the user picks
                            // which calendar and confirms, so Wishlist needs no calendar permission.
                            TextButton(
                                enabled = title.isNotBlank(),
                                onClick = {
                                    context.addToCalendar(
                                        title = title.trim(),
                                        description = memo.trim(),
                                        dateMillis = endDate!!,
                                    )
                                },
                            ) { Text("캘린더 등록") }
                        }
                    },
                )
                MonthEndChips(selected = endDate, onSelect = setItemEndDate)
                Spacer(Modifier.height(16.dp))

                Text("세부항목", style = MaterialTheme.typography.titleSmall)
                subItems.forEachIndexed { index, subItem ->
                    SubItemEditor(
                        subItem = subItem,
                        parentTitle = title,
                        onChange = { subItems[index] = it },
                        onRemove = { subItems.removeAt(index) },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = newSubItemTitle,
                        onValueChange = { newSubItemTitle = it },
                        // Label style left alone so it still shrinks when the field is focused.
                        label = { Text("세부항목 추가") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = SUB_ITEM_TEXT_STYLE,
                    )
                    IconButton(
                        enabled = newSubItemTitle.isNotBlank(),
                        onClick = {
                            // Starts from the 항목's 최종 종료일 and appears with its own 월말 chips
                            // right above this field, so a different date is one more tap away.
                            subItems.add(SubItem(title = newSubItemTitle.trim(), endDate = endDate))
                            newSubItemTitle = ""
                        },
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "세부항목 추가")
                    }
                }
                Spacer(Modifier.height(16.dp))

                // 대분류 | 중분류 on one line: both are free text, with everything already used
                // available from the dropdown.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CategoryField(
                        label = "대분류",
                        value = majorCategory,
                        options = majorCategorySuggestions,
                        colorFor = { categoryColor(it, null, categoryColors) },
                        onValueChange = { majorCategory = it },
                        modifier = Modifier.weight(1f),
                    )
                    CategoryField(
                        label = "중분류",
                        value = minorCategory,
                        options = minorSuggestions,
                        colorFor = { categoryColor(majorCategory, it, categoryColors) },
                        onValueChange = { minorCategory = it },
                        modifier = Modifier.weight(1f),
                    )
                }

                // Colors belong to the category, not to this item, so they're applied the moment
                // they're tapped rather than waiting for 저장 — the same 대분류 in every other item
                // changes with it.
                if (majorCategory.isNotBlank()) {
                    ColorPickerRow(
                        label = "대분류 색",
                        selectedIndex = categoryColors.paletteIndexFor(majorCategory.trim(), null),
                        onSelect = { viewModel.setCategoryColor(majorCategory.trim(), null, it) },
                    )
                }
                if (majorCategory.isNotBlank() && minorCategory.isNotBlank()) {
                    ColorPickerRow(
                        label = "중분류 색",
                        selectedIndex = categoryColors.paletteIndexFor(
                            majorCategory.trim(),
                            minorCategory.trim(),
                        ),
                        onSelect = {
                            viewModel.setCategoryColor(majorCategory.trim(), minorCategory.trim(), it)
                        },
                    )
                }
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

                DateField(
                    label = "시작일",
                    epochMillis = startedAt,
                    onValueChange = { startedAt = it },
                )
                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isDone, onCheckedChange = { isDone = it })
                    Text("완료")
                }
                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (editingItem != null && !isNewItem) {
                        TextButton(onClick = { onDelete(editingItem) }) { Text("삭제") }
                        Spacer(Modifier.width(8.dp))
                    }
                    TextButton(onClick = requestDismiss) { Text("취소") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        enabled = title.isNotBlank(),
                        onClick = { onSave(edited()) },
                    ) { Text("저장") }
                }
            }
        }
    }

    // Declared after the editor so this window sits on top of it.
    if (askBeforeLeaving) {
        AlertDialog(
            // Dismissing the question itself means "keep editing".
            onDismissRequest = { askBeforeLeaving = false },
            title = { Text("변경사항을 저장할까요?") },
            text = { Text("저장하지 않으면 수정한 내용이 사라집니다.") },
            confirmButton = {
                TextButton(
                    enabled = title.isNotBlank(),
                    onClick = { onSave(edited()) },
                ) { Text("저장") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("저장 안 함") }
            },
        )
    }
}

/**
 * One 세부항목, kept to two lines however many of them the 항목 has:
 *
 *     [✓] 항목이름                       [✕]
 *     26.08.31  월말  8말  9말  A말  B말  [✕]
 *
 * The second line scrolls sideways, so the date button, the 월말 shortcuts and the clear button
 * share it instead of stacking into a block per 세부항목.
 */
@Composable
private fun SubItemEditor(
    subItem: SubItem,
    /** Prefixes the calendar event, so "장보기" from "생일 준비" doesn't lose its context. */
    parentTitle: String,
    onChange: (SubItem) -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    var showPicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = subItem.done,
                onCheckedChange = { onChange(subItem.copy(done = it)) },
            )
            OutlinedTextField(
                value = subItem.title,
                onValueChange = { onChange(subItem.copy(title = it)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = SUB_ITEM_TEXT_STYLE,
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "세부항목 삭제")
            }
        }
        // Each 세부항목 carries its own 완료예정일; the main table sorts lines by it.
        MonthEndChips(
            selected = subItem.endDate,
            onSelect = { onChange(subItem.copy(endDate = it)) },
            leadingContent = {
                TextButton(onClick = { showPicker = true }) {
                    Text(
                        text = subItem.endDate?.let(::formatDate) ?: "날짜",
                        style = SUB_ITEM_TEXT_STYLE,
                    )
                }
            },
            trailingContent = {
                val due = subItem.endDate
                if (due != null) {
                    IconButton(
                        enabled = subItem.title.isNotBlank(),
                        onClick = {
                            context.addToCalendar(
                                title = listOf(parentTitle.trim(), subItem.title.trim())
                                    .filter { it.isNotBlank() }
                                    .joinToString(" - "),
                                description = null,
                                dateMillis = due,
                            )
                        },
                    ) {
                        Icon(Icons.Filled.Event, contentDescription = "캘린더에 등록")
                    }
                    IconButton(onClick = { onChange(subItem.copy(endDate = null)) }) {
                        Icon(Icons.Filled.Close, contentDescription = "완료예정일 지우기")
                    }
                }
            },
        )
        Spacer(Modifier.height(8.dp))
    }

    if (showPicker) {
        DateOnlyPicker(
            epochMillis = subItem.endDate,
            onPicked = { onChange(subItem.copy(endDate = it)) },
            onDismiss = { showPicker = false },
        )
    }
}

/**
 * The palette, plus 자동 to go back to the color derived from the name. Scrolls sideways so it
 * doesn't force the dialog wider on a small phone.
 */
@Composable
private fun ColorPickerRow(
    label: String,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        CATEGORY_PALETTE.forEachIndexed { index, color ->
            val selected = selectedIndex == index
            Box(
                modifier = Modifier
                    .size(if (selected) 26.dp else 22.dp)
                    .background(color, CircleShape)
                    .border(
                        width = if (selected) 2.dp else 0.dp,
                        color = MaterialTheme.colorScheme.onSurface,
                        shape = CircleShape,
                    )
                    .clickable { onSelect(index) },
            )
        }
        TextButton(onClick = { onSelect(null) }) {
            Text(
                text = "자동",
                style = if (selectedIndex == null) {
                    MaterialTheme.typography.labelLarge
                } else {
                    MaterialTheme.typography.labelMedium
                },
            )
        }
    }
}

/**
 * A 분류 field: type anything, or pick one of the values already in use from the dropdown. New
 * categories still have to be typeable, so the text field stays editable rather than read-only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryField(
    label: String,
    value: String,
    options: List<String>,
    /** The color the main table paints this category with, shown as a dot next to each option. */
    colorFor: (String) -> Color?,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded && options.isNotEmpty(),
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            trailingIcon = {
                if (options.isNotEmpty()) {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryEditable),
        )
        ExposedDropdownMenu(expanded = expanded && options.isNotEmpty(), onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                val tint = colorFor(option)
                DropdownMenuItem(
                    text = { Text(option) },
                    leadingIcon = tint?.let {
                        {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(it, CircleShape),
                            )
                        }
                    },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
