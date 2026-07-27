package com.wishlist.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wishlist.app.data.WishlistItem
import com.wishlist.app.util.formatPonderedDuration
import kotlinx.coroutines.delay

@Composable
fun WishlistItemRow(
    item: WishlistItem,
    onToggleCompleted: () -> Unit,
    onClick: () -> Unit,
) {
    // Keeps the "고민한 기간" of in-progress items ticking while the row is visible.
    val nowState = remember { mutableLongStateOf(System.currentTimeMillis()) }
    if (!item.isCompleted) {
        LaunchedEffect(item.id) {
            while (true) {
                delay(60_000)
                nowState.value = System.currentTimeMillis()
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(checked = item.isCompleted, onCheckedChange = { onToggleCompleted() })
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = item.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "고민 시작: ${formatEpochMillis(item.startedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                val statusText = if (item.isCompleted) {
                    "완료: ${formatEpochMillis(item.completedAt!!)}"
                } else {
                    "진행 중"
                }
                Text(text = statusText, style = MaterialTheme.typography.bodySmall)
                Text(
                    text = "고민한 기간: ${formatPonderedDuration(item.ponderedDurationMillis(nowState.value))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
