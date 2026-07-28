package com.wishlist.app.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wishlist.app.util.endOfMonthMillis
import com.wishlist.app.util.monthEndLabel

/** How many months the shortcuts cover: 이달 plus the next four. */
private val MONTH_OFFSETS = 0..4

/**
 * One-tap 월말 shortcuts for a date field. Chips carry just the month number so each stays two
 * characters wide; the row is captioned 월말 once instead of repeating it in every chip.
 */
@Composable
fun MonthEndChips(
    selected: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "월말",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MONTH_OFFSETS.forEach { offset ->
            val millis = endOfMonthMillis(offset)
            FilterChip(
                selected = selected == millis,
                onClick = { onSelect(millis) },
                label = { Text(monthEndLabel(offset)) },
            )
        }
    }
}
