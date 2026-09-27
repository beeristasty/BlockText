package com.securingtheinside.blocktext

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ReplyDraftButton(recipient: String) {
    val appContext = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()

    var editorOpen by rememberSaveable(recipient) {
        mutableStateOf(false)
    }

    var draft by rememberSaveable(recipient) {
        mutableStateOf("")
    }

    var sending by remember(recipient) {
        mutableStateOf(false)
    }

    var statusText by remember(recipient) {
        mutableStateOf<String?>(null)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        statusText = if (granted) {
            "SMS permission granted. Tap Send again to submit your reply."
        } else {
            "SMS permission was not granted. You can enable SMS permission " +
                    "in Android's app settings."
        }
    }

    TextButton(
        onClick = {
            statusText = null
            editorOpen = true
        }
    ) {
        Text("Reply")
    }

    if (editorOpen) {
        AlertDialog(
            onDismissRequest = {
                if (!sending) {
                    editorOpen = false
                }
            },
            properties = DialogProperties(
                dismissOnBackPress = !sending,
                dismissOnClickOutside = !sending
            ),
            title = {
                Text("Reply")
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "To: $recipient",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    OutlinedTextField(
                        value = draft,
                        onValueChange = {
                            draft = it
                            statusText = null
                        },
                        enabled = !sending,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Message") },
                        placeholder = { Text("Type your reply") },
                        minLines = 2,
                        maxLines = 4
                    )

                    Text(
                        text = "Single-part SMS only for now. " +
                                "Send success does not confirm delivery.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    statusText?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !sending && draft.isNotBlank(),
                    onClick = {
                        // Also guard here against rapid repeated taps.
                        if (!sending && draft.isNotBlank()) {
                            val permissionGranted =
                                ContextCompat.checkSelfPermission(
                                    appContext,
                                    Manifest.permission.SEND_SMS
                                ) == PackageManager.PERMISSION_GRANTED

                            if (!permissionGranted) {
                                permissionLauncher.launch(
                                    Manifest.permission.SEND_SMS
                                )
                            } else {
                                val outgoingText = draft

                                sending = true
                                statusText = null

                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            SmsSender.submit(
                                                context = appContext,
                                                recipient = recipient,
                                                body = outgoingText
                                            )
                                        }

                                        // The message is now saved in the
                                        // SMS database and submitted to Android.
                                        draft = ""

                                        statusText =
                                            "Submitted to Android. Tap Back, " +
                                                    "then Refresh in the conversation " +
                                                    "to check the result."
                                    } catch (error: CancellationException) {
                                        throw error
                                    } catch (error: Exception) {
                                        // Keep the draft when submission fails.
                                        statusText = error.message
                                            ?: "The message could not be submitted."
                                    } finally {
                                        sending = false
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Text(
                        if (sending) "Submitting..." else "Send"
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !sending,
                    onClick = {
                        editorOpen = false
                    }
                ) {
                    Text("Back")
                }
            }
        )
    }
}