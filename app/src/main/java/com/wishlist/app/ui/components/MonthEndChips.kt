package com.wishlist.app.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wishlist.app.util.endOfMonthMillis
import com.wishlist.app.util.monthEndLabel

/** How many months the shortcuts cover: this month plus the next four. */
private val MONTH_OFFSETS = 0..4

/**
 * One-tap 월말 shortcuts for a date field: 월말 for this month, then 8말 / 9말 / A말 / B말 for the four
 * that follow. Every label is two characters, so five chips fit on a phone.
 */
@Composable
fun MonthEndChips(
    selected: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
    /** Put on the same scrolling line as the chips — used to keep a 세부항목 down to two lines. */
    leadingContent: @Composable RowScope.() -> Unit = {},
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        leadingContent()
        MONTH_OFFSETS.forEach { offset ->
            val millis = endOfMonthMillis(offset)
            FilterChip(
                selected = selected == millis,
                onClick = { onSelect(millis) },
                label = { Text(monthEndLabel(offset)) },
            )
        }
        trailingContent()
    }
}
