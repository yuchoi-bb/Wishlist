package com.wishlist.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.wishlist.app.auth.AuthManager
import kotlinx.coroutines.launch

@Composable
fun SignInScreen(authManager: AuthManager) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val signInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        // Report the real reason rather than swallowing it: Google Sign-In signals config problems
        // through ApiException status codes (10 = DEVELOPER_ERROR, i.e. SHA-1/client-ID mismatch;
        // 12501 = user cancelled), which are otherwise invisible without logcat.
        val account = try {
            GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java)
        } catch (e: ApiException) {
            errorMessage = "Google 로그인 실패 (코드 ${e.statusCode}): ${e.message}"
            null
        }
        if (account == null) {
            if (errorMessage == null) errorMessage = "로그인이 취소되었거나 실패했습니다."
            return@rememberLauncherForActivityResult
        }
        if (account.idToken == null) {
            errorMessage = "ID 토큰을 받지 못했습니다. Firebase 콘솔에서 Google 로그인 설정과 SHA-1 등록을 확인해주세요."
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val user = runCatching { authManager.signInWithGoogle(account) }
            errorMessage = when {
                user.isFailure -> "Firebase 로그인 실패: ${user.exceptionOrNull()?.message}"
                user.getOrNull() == null -> "Firebase 로그인에 실패했습니다."
                else -> null
            }
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "여러 기기에서 실시간으로 동기화하려면\nGoogle 계정으로 로그인하세요.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(24.dp))

            if (!authManager.isConfigured) {
                Text(
                    text = "Firebase 설정이 아직 완료되지 않았습니다 (google-services.json 필요).",
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            } else {
                Button(onClick = { signInLauncher.launch(authManager.signInClient(context).signInIntent) }) {
                    Text("Google 계정으로 로그인")
                }
            }

            errorMessage?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
        }
    }
}
