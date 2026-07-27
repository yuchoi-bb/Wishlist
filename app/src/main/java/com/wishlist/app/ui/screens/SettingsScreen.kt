package com.wishlist.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.wishlist.app.data.FirestoreWishlistRepository
import com.wishlist.app.data.WishlistDatabase
import com.wishlist.app.update.UpdateChecker
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(authManager: AuthManager, onBack: () -> Unit) {
    val context = LocalContext.current
    val database = remember { WishlistDatabase.getInstance(context) }
    val firestoreRepository = remember { FirestoreWishlistRepository(FirebaseFirestore.getInstance()) }
    val backupManager = remember { DriveBackupManager(context, database, firestoreRepository) }
    val updateChecker = remember { UpdateChecker(context) }
    val scope = rememberCoroutineScope()

    val currentUser by authManager.currentUser.collectAsStateWithLifecycle()
    var driveReauthTrigger by remember { mutableIntStateOf(0) }
    val account = remember(driveReauthTrigger) { backupManager.signedInAccount() }
    var statusMessage by remember { mutableStateOf<String?>(null) }

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

            val uid = currentUser?.uid
            if (account == null || uid == null) {
                Text("Drive 접근 권한이 없습니다.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { driveSignInLauncher.launch(authManager.signInClient(context).signInIntent) }) {
                    Text("Drive 접근 허용")
                }
            } else {
                Button(onClick = {
                    scope.launch {
                        val result = backupManager.backup(account, uid)
                        statusMessage = if (result.isSuccess) {
                            "백업이 완료되었습니다"
                        } else {
                            describeDriveFailure("백업", result.exceptionOrNull())
                        }
                    }
                }) {
                    Text("지금 백업하기")
                }
                OutlinedButton(onClick = {
                    scope.launch {
                        val result = backupManager.restore(account, uid)
                        statusMessage = if (result.isSuccess) {
                            "복원이 완료되었습니다"
                        } else {
                            describeDriveFailure("복원", result.exceptionOrNull())
                        }
                    }
                }) {
                    Text("백업에서 복원하기")
                }
            }

            Text("자동 업데이트", style = MaterialTheme.typography.titleMedium)
            Text("현재 버전: ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = {
                scope.launch {
                    val update = updateChecker.checkForUpdate()
                    if (update != null) {
                        updateChecker.downloadUpdate(update)
                        statusMessage = "새 버전 ${update.version} 다운로드를 시작했습니다"
                    } else {
                        statusMessage = "최신 버전을 사용 중입니다"
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
