package com.wishlist.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wishlist.app.util.formatDate
import java.time.Instant
import java.time.ZoneId

/**
 * A date-only field. [epochMillis] is null when unset (used by 완료일 to mean "still in progress").
 * The picker works in UTC internally — Material3's DatePicker reports UTC-midnight millis — and the
 * chosen calendar date is converted back to local start-of-day before being handed out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    epochMillis: Long?,
    emptyLabel: String = "지정 안 됨",
    onValueChange: (Long) -> Unit,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    var showPicker by remember { mutableStateOf(false) }

    Column {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { showPicker = true }) {
                Text(epochMillis?.let(::formatDate) ?: emptyLabel)
            }
            trailingContent?.invoke()
        }
    }

    if (showPicker) {
        val zone = ZoneId.systemDefault()
        val initialLocalDate = Instant.ofEpochMilli(epochMillis ?: System.currentTimeMillis())
            .atZone(zone)
            .toLocalDate()
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialLocalDate
                .atStartOfDay(ZoneId.of("UTC"))
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { selected ->
                        val date = Instant.ofEpochMilli(selected).atZone(ZoneId.of("UTC")).toLocalDate()
                        onValueChange(date.atStartOfDay(zone).toInstant().toEpochMilli())
                    }
                    showPicker = false
                }) { Text("확인") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("취소") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
