package com.securingtheinside.blocktext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PhraseRulesButton() {
    val appContext = LocalContext.current.applicationContext

    val store = remember(appContext) {
        PhraseRuleStore(appContext)
    }

    var phrases by remember(store) {
        mutableStateOf(store.getPhrases())
    }

    var showRules by rememberSaveable {
        mutableStateOf(false)
    }

    var newPhrase by rememberSaveable {
        mutableStateOf("")
    }

    var saving by remember {
        mutableStateOf(false)
    }

    var errorText by remember {
        mutableStateOf<String?>(null)
    }

    val scope = rememberCoroutineScope()

    DisposableEffect(store) {
        val stopObserving = store.observeChanges {
            phrases = store.getPhrases()
        }

        phrases = store.getPhrases()

        onDispose {
            stopObserving()
        }
    }

    // Save changes without blocking the screen's main thread.
    fun runEdit(
        change: () -> Unit,
        onSuccess: () -> Unit = {}
    ) {
        if (saving) return

        saving = true
        errorText = null

        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    change()
                }

                onSuccess()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorText = error.message ?: "Could not save the change."
            } finally {
                saving = false
            }
        }
    }

    TextButton(
        onClick = {
            errorText = null
            showRules = true
        }
    ) {
        Text("Spam phrases (${phrases.size})")
    }

    if (showRules) {
        AlertDialog(
            onDismissRequest = {
                if (!saving) {
                    showRules = false
                }
            },
            title = {
                Text("Spam phrases")
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Matching messages go to Spam unless the sender is " +
                                "always allowed. Explicit sender blocks take priority.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    OutlinedTextField(
                        value = newPhrase,
                        onValueChange = {
                            newPhrase = it
                            errorText = null
                        },
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Word or phrase") },
                        placeholder = { Text("For example: unpaid toll") },
                        singleLine = true
                    )

                    Button(
                        enabled = !saving && newPhrase.isNotBlank(),
                        onClick = {
                            val phraseToSave = newPhrase

                            runEdit(
                                change = {
                                    store.addPhrase(phraseToSave)
                                },
                                onSuccess = {
                                    newPhrase = ""
                                }
                            )
                        }
                    ) {
                        Text(if (saving) "Saving..." else "Add phrase")
                    }

                    errorText?.let { message ->
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    if (phrases.isEmpty()) {
                        Text("No phrases saved.")
                    } else {
                        phrases.sorted().forEach { phrase ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = phrase,
                                    modifier = Modifier.weight(1f)
                                )

                                TextButton(
                                    enabled = !saving,
                                    onClick = {
                                        runEdit(
                                            change = {
                                                store.removePhrase(phrase)
                                            }
                                        )
                                    }
                                ) {
                                    Text("Remove")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !saving,
                    onClick = {
                        showRules = false
                    }
                ) {
                    Text("Done")
                }
            }
        )
    }
}