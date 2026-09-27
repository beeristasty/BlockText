package com.securingtheinside.blocktext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SenderRuleButtons(sender: String) {
    val appContext = LocalContext.current.applicationContext

    val store = remember(appContext) {
        BlockListStore(appContext)
    }

    var rules by remember(store) {
        mutableStateOf(store.getRules())
    }

    var saving by remember(sender) {
        mutableStateOf(false)
    }

    var errorText by remember(sender) {
        mutableStateOf<String?>(null)
    }

    val normalizedSender = remember(sender) {
        SenderNormalizer.normalize(sender)
    }

    val isBlocked = normalizedSender in rules.blockedSenders
    val isAllowed = normalizedSender in rules.allowedSenders

    val canEdit = !saving &&
            sender.isNotBlank() &&
            sender != "Unknown"

    val scope = rememberCoroutineScope()

    DisposableEffect(store) {
        val stopObserving = store.observeChanges {
            rules = store.getRules()
        }

        rules = store.getRules()

        onDispose {
            stopObserving()
        }
    }

    fun saveChange(change: () -> Unit) {
        if (saving) return

        saving = true
        errorText = null

        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    change()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorText = error.message ?: "Could not save sender settings."
            } finally {
                saving = false
            }
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                modifier = Modifier.weight(1f),
                enabled = canEdit,
                onClick = {
                    val shouldBlock = !isBlocked

                    saveChange {
                        store.setBlocked(sender, shouldBlock)
                    }
                }
            ) {
                Text(
                    if (isBlocked) "Unblock sender" else "Block sender"
                )
            }

            OutlinedButton(
                modifier = Modifier.weight(1f),
                enabled = canEdit,
                onClick = {
                    val shouldAllow = !isAllowed

                    saveChange {
                        store.setAllowed(sender, shouldAllow)
                    }
                }
            ) {
                Text(
                    if (isAllowed) "Remove exception" else "Always allow"
                )
            }
        }

        when {
            saving -> {
                Text(
                    text = "Saving sender settings...",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            isBlocked -> {
                Text(
                    text = "On your block list.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            isAllowed -> {
                Text(
                    text = "Always allowed sender.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        errorText?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}