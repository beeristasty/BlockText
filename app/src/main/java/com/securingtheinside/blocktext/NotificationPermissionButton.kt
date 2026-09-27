package com.securingtheinside.blocktext

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat

@Composable
fun NotificationPermissionButton() {
    val context = LocalContext.current

    var notificationsEnabled by remember(context) {
        mutableStateOf(
            NotificationManagerCompat.from(context)
                .areNotificationsEnabled()
        )
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        notificationsEnabled = NotificationManagerCompat.from(context)
            .areNotificationsEnabled()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        notificationsEnabled = NotificationManagerCompat.from(context)
            .areNotificationsEnabled()
    }

    val settingsIntent = Intent(
        Settings.ACTION_APP_NOTIFICATION_SETTINGS
    ).putExtra(
        Settings.EXTRA_APP_PACKAGE,
        context.packageName
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (notificationsEnabled) {
            Text("Notifications: allowed")
        } else {
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(
                            Manifest.permission.POST_NOTIFICATIONS
                        )
                    } else {
                        settingsLauncher.launch(settingsIntent)
                    }
                }
            ) {
                Text("Enable alerts")
            }
        }

        TextButton(
            onClick = {
                settingsLauncher.launch(settingsIntent)
            }
        ) {
            Text("Alert settings")
        }
    }
}