package com.wishlist.app.ui.screens

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.wishlist.app.BuildConfig
import com.wishlist.app.auth.AuthManager
import com.wishlist.app.backup.DriveBackupManager
import com.wishlist.app.data.ArcDatabase
import com.wishlist.app.data.FirestoreArcRepository
import com.wishlist.app.ui.theme.DeviceSettings
import com.wishlist.app.ui.theme.ThemeMode
import com.wishlist.app.ui.theme.ViewMode
import com.wishlist.app.update.UpdateChecker
import com.wishlist.app.util.formatDateTime
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(authManager: AuthManager, onBack: () -> Unit) {
    val context = LocalContext.current
    val database = remember { ArcDatabase.getInstance(context) }
    val firestoreRepository = remember { FirestoreArcRepository(FirebaseFirestore.getInstance()) }
    val backupManager = remember { DriveBackupManager(context, database, firestoreRepository) }
    val updateChecker = remember { UpdateChecker(context) }
    val deviceSettings = remember { DeviceSettings.get(context) }
    val themeMode by deviceSettings.themeMode.collectAsStateWithLifecycle()
    val viewMode by deviceSettings.viewMode.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val currentUser by authManager.currentUser.collectAsStateWithLifecycle()
    var driveReauthTrigger by remember { mutableIntStateOf(0) }
    val account = remember(driveReauthTrigger) { backupManager.signedInAccount() }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    var busyLabel by remember { mutableStateOf<String?>(null) }
    var lastBackupAt by remember { mutableStateOf<Long?>(null) }
    var lastRestoreAt by remember { mutableStateOf(backupPrefs(context).getLong(KEY_LAST_RESTORE, 0L).takeIf { it > 0 }) }
    // Bumped after a backup so the "마지막 백업" line refetches instead of showing a stale time.
    var backupInfoTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(account, backupInfoTrigger) {
        val currentAccount = account ?: return@LaunchedEffect
        lastBackupAt = backupManager.lastBackupAt(currentAccount).getOrNull()
    }

    val driveSignInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        driveReauthTrigger++
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            // Scrollable: backup/update failures print the server's full response, which runs well
            // past one screen and was previously cut off with no way to read the rest.
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("화면 테마", style = MaterialTheme.typography.titleMedium)
            // Stored per device: the same account is used on a tablet in dark mode and a phone in
            // light mode, so this follows the device rather than the person.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = themeMode == mode,
                        onClick = { deviceSettings.setThemeMode(mode) },
                        label = { Text(mode.label) },
                    )
                }
            }

            Text("메인 화면", style = MaterialTheme.typography.titleMedium)
            // Also a one-tap toggle in the app bar; this is where the two are spelled out.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ViewMode.entries.forEach { mode ->
                    FilterChip(
                        selected = viewMode == mode,
                        onClick = { deviceSettings.setViewMode(mode) },
                        label = { Text(mode.label) },
                    )
                }
            }
            Text(viewMode.description, style = MaterialTheme.typography.bodySmall)

            Text("계정", style = MaterialTheme.typography.titleMedium)
            // Falls back through displayName/uid: email is null unless the sign-in requested it,
            // and a bare "알 수 없음" made a working sign-in look broken.
            val accountLabel = currentUser?.let { user ->
                user.email ?: user.displayName ?: "uid ${user.uid.take(8)}…"
            } ?: "로그인되지 않음"
            Text("로그인: $accountLabel", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = {
                GoogleSignIn.getClient(context, GoogleSignInOptions.DEFAULT_SIGN_IN).signOut()
                authManager.signOut()
            }) {
                Text("로그아웃")
            }

            Text("Google Drive 백업", style = MaterialTheme.typography.titleMedium)
            Text(
                "앱 전용 저장 공간(appDataFolder)에 백업합니다. 이 백업은 사용자가 직접 실행해야 합니다. " +
                    "항목 자체는 Firestore로 실시간 동기화되며, 이와 별개로 OS 차원의 자동 백업도 항상 동작합니다.",
                style = MaterialTheme.typography.bodySmall,
            )

            Text(
                text = "마지막 백업: " + (lastBackupAt?.let { formatDateTime(it) } ?: "없음"),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "마지막 복원: " + (lastRestoreAt?.let { formatDateTime(it) } ?: "없음"),
                style = MaterialTheme.typography.bodyMedium,
            )

            val uid = currentUser?.uid
            if (account == null || uid == null) {
                Text("Drive 접근 권한이 없습니다.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { driveSignInLauncher.launch(authManager.signInClient(context).signInIntent) }) {
                    Text("Drive 접근 허용")
                }
            } else {
                // Both actions take a few seconds against Drive with no other visible sign that
                // anything happened, so show what's running and block a second tap meanwhile.
                busyLabel?.let { label ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Button(
                    enabled = busyLabel == null,
                    onClick = {
                        scope.launch {
                            busyLabel = "백업 중…"
                            statusMessage = null
                            val result = backupManager.backup(account, uid)
                            busyLabel = null
                            statusMessage = if (result.isSuccess) {
                                backupInfoTrigger++
                                "백업이 완료되었습니다"
                            } else {
                                describeDriveFailure("백업", result.exceptionOrNull())
                            }
                        }
                    },
                ) {
                    Text("지금 백업하기")
                }
                OutlinedButton(
                    enabled = busyLabel == null,
                    onClick = {
                        scope.launch {
                            busyLabel = "복원 중…"
                            statusMessage = null
                            val result = backupManager.restore(account, uid)
                            busyLabel = null
                            statusMessage = if (result.isSuccess) {
                                val now = System.currentTimeMillis()
                                backupPrefs(context).edit().putLong(KEY_LAST_RESTORE, now).apply()
                                lastRestoreAt = now
                                "복원이 완료되었습니다"
                            } else {
                                describeDriveFailure("복원", result.exceptionOrNull())
                            }
                        }
                    },
                ) {
                    Text("백업에서 복원하기")
                }
            }

            Text("자동 업데이트", style = MaterialTheme.typography.titleMedium)
            Text("현재 버전: ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
            // Same path the launch-time updater takes, just started by hand: download (or reuse an
            // APK already on disk) and go straight to the installer.
            OutlinedButton(onClick = {
                scope.launch {
                    statusMessage = "업데이트 확인 중…"
                    val update = updateChecker.checkForUpdate()
                    if (update == null) {
                        statusMessage = "최신 버전을 사용 중입니다"
                        return@launch
                    }
                    val apk = updateChecker.downloadedApk(update) ?: run {
                        val id = updateChecker.startDownload(update)
                        updateChecker.awaitDownload(id, update.version) { percent ->
                            statusMessage = "새 버전 ${update.version} 받는 중 $percent%"
                        }
                    }
                    statusMessage = when {
                        apk == null -> "다운로드에 실패했습니다"
                        updateChecker.canInstall() -> {
                            context.startActivity(updateChecker.installIntent(apk))
                            "새 버전 ${update.version} 설치를 시작합니다"
                        }
                        else -> {
                            context.startActivity(updateChecker.installPermissionIntent())
                            "설치를 허용한 뒤 다시 눌러주세요"
                        }
                    }
                }
            }) {
                Text("업데이트 확인")
            }

            // Selectable so a failure can be copied out; the raw Google API error is long and the
            // useful part is buried in it.
            statusMessage?.let {
                SelectionContainer {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/**
 * Drive's own error text is a wall of JSON. Lead with the actionable cause where we can recognise
 * it — a 403 here is almost always the Drive API simply not being enabled for the Cloud project,
 * which no amount of retrying or re-consenting fixes — and keep the raw response underneath.
 */
private const val BACKUP_PREFS = "backup_state"
private const val KEY_LAST_RESTORE = "last_restore_at"

/** Restores are a per-device action, so unlike the backup time there's nothing on Drive to read. */
private fun backupPrefs(context: Context) =
    context.getSharedPreferences(BACKUP_PREFS, Context.MODE_PRIVATE)

private fun describeDriveFailure(action: String, error: Throwable?): String {
    val raw = error?.message.orEmpty()
    val hint = when {
        raw.contains("SERVICE_DISABLED") || raw.contains("accessNotConfigured") ->
            "이 프로젝트에서 Google Drive API가 켜져 있지 않습니다. " +
                "console.cloud.google.com/apis/library/drive.googleapis.com 에서 사용 설정해주세요."
        raw.contains("403") ->
            "권한이 거부되었습니다. Google Cloud 콘솔에서 Drive API가 사용 설정되어 있는지, " +
                "로그인 시 Drive 접근을 허용했는지 확인해주세요."
        raw.contains("401") -> "인증이 만료되었습니다. 로그아웃 후 다시 로그인해주세요."
        else -> null
    }
    return listOfNotNull("$action 실패", hint, raw).joinToString("\n\n")
}
