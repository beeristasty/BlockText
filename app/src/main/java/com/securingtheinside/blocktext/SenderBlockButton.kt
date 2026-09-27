package com.securingtheinside.blocktext

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SenderBlockButton(sender: String) {
    val appContext = LocalContext.current.applicationContext

    val store = remember(appContext) {
        BlockListStore(appContext)
    }

    var isBlocked by remember(sender, store) {
        mutableStateOf(store.isBlocked(sender))
    }

    var saving by remember(sender) {
        mutableStateOf(false)
    }

    var saveError by remember(sender) {
        mutableStateOf<String?>(null)
    }

    val scope = rememberCoroutineScope()

    DisposableEffect(store, sender) {
        val stopObserving = store.observeChanges {
            isBlocked = store.isBlocked(sender)
        }

        isBlocked = store.isBlocked(sender)

        onDispose {
            stopObserving()
        }
    }

    Column {
        Button(
            enabled = !saving &&
                    sender.isNotBlank() &&
                    sender != "Unknown",
            onClick = {
                val newBlockedState = !isBlocked

                scope.launch {
                    saving = true
                    saveError = null

                    try {
                        withContext(Dispatchers.IO) {
                            store.setBlocked(sender, newBlockedState)
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        saveError = "Could not save the change. Try again."
                    } finally {
                        saving = false
                    }
                }
            }
        ) {
            Text(
                text = when {
                    saving -> "Saving..."
                    isBlocked -> "Unblock sender"
                    else -> "Block sender"
                }
            )
        }

        if (isBlocked) {
            Text(
                text = "On your block list.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        saveError?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}