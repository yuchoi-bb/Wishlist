package com.wishlist.app.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.wishlist.app.ui.theme.WishlistTheme
import com.wishlist.app.update.UpdateChecker

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
                WishlistRoot()
            }

            LaunchedEffect(Unit) {
                val checker = UpdateChecker(applicationContext)
                val update = checker.checkForUpdate()
                if (update != null) {
                    checker.downloadUpdate(update)
                }
            }
        }
    }
}
