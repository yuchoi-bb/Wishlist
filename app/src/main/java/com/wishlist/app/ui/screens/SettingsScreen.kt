package com.wishlist.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.wishlist.app.BuildConfig
import com.wishlist.app.backup.DriveBackupManager
import com.wishlist.app.data.WishlistDatabase
import com.wishlist.app.update.UpdateChecker
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val database = remember { WishlistDatabase.getInstance(context) }
    val backupManager = remember { DriveBackupManager(context, database) }
    val updateChecker = remember { UpdateChecker(context) }
    val scope = rememberCoroutineScope()

    var account by remember { mutableStateOf(backupManager.signedInAccount()) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val signInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val signedIn = runCatching { GoogleSignIn.getSignedInAccountFromIntent(result.data).result }.getOrNull()
        account = signedIn
        statusMessage = if (signedIn != null) "로그인되었습니다: ${signedIn.email}" else "로그인에 실패했습니다"
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
            modifier = Modifier.padding(padding).fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Google Drive 백업", style = MaterialTheme.typography.titleMedium)
            Text(
                "앱 전용 저장 공간(appDataFolder)에 백업합니다. 이 백업은 사용자가 직접 실행해야 합니다. " +
                    "이와 별개로 OS 차원의 자동 백업이 항상 동작합니다.",
                style = MaterialTheme.typography.bodySmall,
            )

            if (account == null) {
                Button(onClick = { signInLauncher.launch(backupManager.signInClient().signInIntent) }) {
                    Text("Google 계정으로 로그인")
                }
            } else {
                Text("로그인 계정: ${account?.email}")
                Button(onClick = {
                    scope.launch {
                        val result = backupManager.backup(account!!)
                        statusMessage = if (result.isSuccess) "백업이 완료되었습니다" else "백업 실패: ${result.exceptionOrNull()?.message}"
                    }
                }) {
                    Text("지금 백업하기")
                }
                OutlinedButton(onClick = {
                    scope.launch {
                        val result = backupManager.restore(account!!)
                        statusMessage = if (result.isSuccess) "복원이 완료되었습니다" else "복원 실패: ${result.exceptionOrNull()?.message}"
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

            statusMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
