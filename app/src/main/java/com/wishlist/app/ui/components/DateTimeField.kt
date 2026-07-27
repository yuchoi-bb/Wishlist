package com.wishlist.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val displayFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")

fun formatEpochMillis(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(displayFormatter)

/** A field showing a date+time value that opens date then time pickers to edit it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimeField(
    label: String,
    epochMillis: Long,
    onValueChange: (Long) -> Unit,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var pendingDate by remember { mutableStateOf<LocalDate?>(null) }

    Column {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { showDatePicker = true }) {
                Text(formatEpochMillis(epochMillis))
            }
            trailingContent?.invoke()
        }
    }

    if (showDatePicker) {
        val zone = ZoneId.systemDefault()
        val current = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = current.toLocalDate()
                .atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val selectedMillis = datePickerState.selectedDateMillis
                    if (selectedMillis != null) {
                        pendingDate = Instant.ofEpochMilli(selectedMillis).atZone(ZoneId.of("UTC")).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("다음") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("취소") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    val datePendingForTime = pendingDate
    if (datePendingForTime != null) {
        val zone = ZoneId.systemDefault()
        val current = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalTime()
        val timePickerState = rememberTimePickerState(
            initialHour = current.hour,
            initialMinute = current.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { pendingDate = null },
            confirmButton = {
                TextButton(onClick = {
                    val newTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    val newMillis = datePendingForTime.atTime(newTime)
                        .atZone(zone).toInstant().toEpochMilli()
                    onValueChange(newMillis)
                    pendingDate = null
                }) { Text("확인") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDate = null }) { Text("취소") }
            },
            text = { TimePicker(state = timePickerState) },
        )
    }
}
