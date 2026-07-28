package com.wishlist.app.update

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File

/** What the updater is doing right now, and all the banner ever shows. */
private sealed interface UpdateState {
    data object Idle : UpdateState
    data class Downloading(val version: String, val percent: Int) : UpdateState
    data class NeedsPermission(val version: String) : UpdateState
    data class Failed(val version: String) : UpdateState
}

/**
 * Checks for a new release on launch and carries it through to the installer on its own: download,
 * then open the system installer as soon as the file lands. The user taps nothing unless Android
 * asks them to — the installer's own confirmation, and the one-time "이 출처 허용" switch, are the
 * only steps a normal app cannot skip.
 *
 * Shows a thin progress banner while downloading and stays invisible the rest of the time.
 */
@Composable
fun AutoUpdater(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val checker = remember { UpdateChecker(context.applicationContext) }
    var state by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    // Held across the trip to the settings screen so the install can resume on the way back.
    var readyApk by remember { mutableStateOf<File?>(null) }

    var pendingVersion by remember { mutableStateOf("") }

    val install: (File) -> Unit = { file ->
        runCatching { context.startActivity(checker.installIntent(file)) }
            .onSuccess { state = UpdateState.Idle }
            .onFailure { state = UpdateState.Failed(pendingVersion) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        // Returning from the settings screen: if the switch is on now, go straight to installing.
        val file = readyApk
        if (file != null && checker.canInstall()) install(file)
    }

    LaunchedEffect(Unit) {
        val update = checker.checkForUpdate() ?: return@LaunchedEffect
        // A finished download from an earlier launch is reused, so declining the installer once
        // doesn't mean fetching the whole APK again next time.
        val apk = checker.downloadedApk(update) ?: run {
            state = UpdateState.Downloading(update.version, 0)
            val id = checker.startDownload(update)
            checker.awaitDownload(id, update.version) { percent ->
                state = UpdateState.Downloading(update.version, percent)
            }
        }
        if (apk == null) {
            state = UpdateState.Failed(update.version)
            return@LaunchedEffect
        }
        readyApk = apk
        pendingVersion = update.version
        if (checker.canInstall()) {
            install(apk)
        } else {
            state = UpdateState.NeedsPermission(update.version)
        }
    }

    if (state != UpdateState.Idle) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = modifier.fillMaxWidth(),
        ) {
            when (val current = state) {
                is UpdateState.Downloading -> Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = "새 버전 ${current.version} 받는 중 ${current.percent}%",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    LinearProgressIndicator(
                        progress = { current.percent / 100f },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }

                is UpdateState.NeedsPermission -> Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "새 버전 ${current.version} 준비됨",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(
                        onClick = { permissionLauncher.launch(checker.installPermissionIntent()) },
                    ) {
                        Text("설치 허용")
                    }
                }

                is UpdateState.Failed -> Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "업데이트 ${current.version} 다운로드 실패",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = { state = UpdateState.Idle }) { Text("닫기") }
                }

                UpdateState.Idle -> Unit
            }
        }
    }
}
