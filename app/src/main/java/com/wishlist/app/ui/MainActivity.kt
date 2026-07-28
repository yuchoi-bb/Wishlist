package com.wishlist.app.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.wishlist.app.CrashLog
import com.wishlist.app.ui.screens.CrashScreen
import com.wishlist.app.ui.theme.WishlistTheme
import com.wishlist.app.update.AutoUpdater

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PermissionChecker.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            WishlistTheme {
                val context = LocalContext.current
                var crash by remember { mutableStateOf(CrashLog.readAndClear(context)) }

                Column {
                    // Checks on every launch and goes all the way to the installer by itself; the
                    // banner only appears while something is actually happening.
                    AutoUpdater()
                    // Weighted so the banner takes its height off the top instead of the screen
                    // below it drawing past the bottom edge.
                    Box(Modifier.weight(1f)) {
                        if (crash != null) {
                            // Shown instead of the normal screen so a crash-on-launch bug still
                            // surfaces this, rather than looping straight back into whatever just
                            // crashed.
                            CrashScreen(crash = crash!!, onDismiss = { crash = null })
                        } else {
                            WishlistRoot()
                        }
                    }
                }
            }
        }
    }
}
