package com.wishlist.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.wishlist.app.CrashInfo

/** Shown on the launch right after a crash. Leads with a short, read-it-aloud code; the full
 * trace is still there below for when more detail is wanted. */
@Composable
fun CrashScreen(crash: CrashInfo, onDismiss: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("최근 앱 크래시 정보", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            Text("이 코드만 말씀해주시면 됩니다:", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Card {
                SelectionContainer {
                    Text(
                        text = crash.code,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("(참고용 전체 내용)", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            SelectionContainer {
                Text(
                    text = crash.fullTrace,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Spacer(Modifier.height(16.dp))
            Button(onClick = onDismiss) { Text("확인, 계속 진행") }
        }
    }
}
