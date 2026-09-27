package com.securingtheinside.blocktext

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
fun ConversationButton(
    message: InboxMessage,
    includeSpam: Boolean = false
) {
    var showConversation by remember(message.id, includeSpam) {
        mutableStateOf(false)
    }

    TextButton(
        onClick = { showConversation = true }
    ) {
        Text("View conversation")
    }

    if (showConversation) {
        ConversationDialog(
            message = message,
            includeSpam = includeSpam,
            onDismiss = { showConversation = false }
        )
    }
}

@Composable
private fun ConversationDialog(
    message: InboxMessage,
    includeSpam: Boolean,
    onDismiss: () -> Unit
) {
    val appContext = LocalContext.current.applicationContext

    val senderStore = remember(appContext) {
        BlockListStore(appContext)
    }

    val phraseStore = remember(appContext) {
        PhraseRuleStore(appContext)
    }

    var conversation by remember(message.id, includeSpam) {
        mutableStateOf<List<ConversationEntry>>(emptyList())
    }

    var loading by remember(message.id, includeSpam) {
        mutableStateOf(true)
    }

    var errorText by remember(message.id, includeSpam) {
        mutableStateOf<String?>(null)
    }

    var refreshCount by remember(message.id) {
        mutableStateOf(0)
    }

    val dateFormat = remember {
        DateFormat.getDateTimeInstance(
            DateFormat.SHORT,
            DateFormat.SHORT
        )
    }

    // Reload when messages or filtering settings change.
    DisposableEffect(appContext, message.id, senderStore, phraseStore) {
        var active = true
        var registered = false

        val requestReload: () -> Unit = {
            if (active) {
                refreshCount++
            }
        }

        val observer = object : ContentObserver(
            Handler(Looper.getMainLooper())
        ) {
            override fun onChange(selfChange: Boolean) {
                requestReload()
            }
        }

        val stopWatchingSenders = senderStore.observeChanges(requestReload)
        val stopWatchingPhrases = phraseStore.observeChanges(requestReload)

        try {
            appContext.contentResolver.registerContentObserver(
                Telephony.Sms.CONTENT_URI,
                true,
                observer
            )

            registered = true
        } catch (_: SecurityException) {
            errorText = "SMS permission is needed to watch this conversation."
        }

        onDispose {
            active = false

            if (registered) {
                appContext.contentResolver.unregisterContentObserver(observer)
            }

            stopWatchingSenders()
            stopWatchingPhrases()
        }
    }

    LaunchedEffect(message.id, includeSpam, refreshCount) {
        loading = true
        errorText = null

        try {
            conversation = withContext(Dispatchers.IO) {
                readConversation(
                    context = appContext,
                    messageId = message.id,
                    includeSpam = includeSpam
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: SecurityException) {
            if (currentCoroutineContext().isActive) {
                errorText = "SMS permission is needed to read this conversation."
            }
        } catch (error: Exception) {
            if (currentCoroutineContext().isActive) {
                errorText =
                    "Could not load conversation: ${error.javaClass.simpleName}"
            }
        } finally {
            if (currentCoroutineContext().isActive) {
                loading = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(message.sender)
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (includeSpam) {
                        "Up to 100 recent SMS, including Spam. " +
                                "Newest at the bottom."
                    } else {
                        "Latest 100 SMS checked; incoming Spam is hidden. " +
                                "Newest visible messages are at the bottom."
                    },
                    style = MaterialTheme.typography.bodySmall
                )

                ReplyDraftButton(recipient = message.sender)

                when {
                    errorText != null -> {
                        Text(
                            text = errorText.orEmpty(),
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    loading && conversation.isEmpty() -> {
                        Text("Loading conversation...")
                    }

                    conversation.isEmpty() -> {
                        Text("No visible messages in this view.")
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            reverseLayout = true,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(
                                items = conversation,
                                key = { entry -> entry.id }
                            ) { entry ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalArrangement =
                                        Arrangement.spacedBy(4.dp)
                                ) {
                                    val status = when {
                                        entry.spamReason != null -> "Spam"

                                        entry.type ==
                                                Telephony.Sms.MESSAGE_TYPE_INBOX ->
                                            "Received"

                                        entry.type ==
                                                Telephony.Sms.MESSAGE_TYPE_SENT ->
                                            "Sent"

                                        entry.type ==
                                                Telephony.Sms.MESSAGE_TYPE_FAILED ->
                                            "Failed to send"

                                        entry.type ==
                                                Telephony.Sms.MESSAGE_TYPE_OUTBOX ||
                                                entry.type ==
                                                Telephony.Sms.MESSAGE_TYPE_QUEUED ->
                                            "Waiting to send"

                                        else -> "Message"
                                    }

                                    Text(
                                        text = status,
                                        style =
                                            MaterialTheme.typography.labelMedium
                                    )

                                    entry.spamReason?.let { reason ->
                                        Text(
                                            text = reason,
                                            style =
                                                MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }

                                    Text(
                                        text = entry.body,
                                        style =
                                            MaterialTheme.typography.bodyMedium
                                    )

                                    Text(
                                        text = dateFormat.format(
                                            Date(entry.timestampMillis)
                                        ),
                                        style =
                                            MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !loading,
                onClick = { refreshCount++ }
            ) {
                Text("Refresh")
            }
        }
    )
}

private data class ConversationEntry(
    val id: Long,
    val body: String,
    val timestampMillis: Long,
    val type: Int,
    val spamReason: String?
)

private fun readConversation(
    context: Context,
    messageId: Long,
    includeSpam: Boolean
): List<ConversationEntry> {
    val resolver = context.contentResolver
    val senderRules = BlockListStore(context).getRules()
    val blockedPhrases = PhraseRuleStore(context).getPhrases()

    val selectedMessageUri = ContentUris.withAppendedId(
        Telephony.Sms.CONTENT_URI,
        messageId
    )

    val threadId = resolver.query(
        selectedMessageUri,
        arrayOf(Telephony.Sms.THREAD_ID),
        null,
        null,
        null
    )?.use { cursor ->
        if (!cursor.moveToFirst()) {
            throw IllegalStateException("The selected message no longer exists")
        }

        cursor.getLong(
            cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
        )
    } ?: throw IllegalStateException("SMS database unavailable")

    if (threadId <= 0) {
        throw IllegalStateException("The message has no conversation ID")
    }

    val projection = arrayOf(
        Telephony.Sms._ID,
        Telephony.Sms.ADDRESS,
        Telephony.Sms.BODY,
        Telephony.Sms.DATE,
        Telephony.Sms.TYPE
    )

    val selection =
        "${Telephony.Sms.THREAD_ID} = ? AND " +
                "${Telephony.Sms.TYPE} IN (?, ?, ?, ?, ?)"

    val selectionArgs = arrayOf(
        threadId.toString(),
        Telephony.Sms.MESSAGE_TYPE_INBOX.toString(),
        Telephony.Sms.MESSAGE_TYPE_SENT.toString(),
        Telephony.Sms.MESSAGE_TYPE_OUTBOX.toString(),
        Telephony.Sms.MESSAGE_TYPE_FAILED.toString(),
        Telephony.Sms.MESSAGE_TYPE_QUEUED.toString()
    )

    val sortOrder =
        "${Telephony.Sms.DATE} DESC, ${Telephony.Sms._ID} DESC"

    val cursor = resolver.query(
        Telephony.Sms.CONTENT_URI,
        projection,
        selection,
        selectionArgs,
        sortOrder
    ) ?: throw IllegalStateException("SMS database unavailable")

    return cursor.use { rows ->
        val idColumn = rows.getColumnIndexOrThrow(Telephony.Sms._ID)
        val senderColumn = rows.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
        val bodyColumn = rows.getColumnIndexOrThrow(Telephony.Sms.BODY)
        val dateColumn = rows.getColumnIndexOrThrow(Telephony.Sms.DATE)
        val typeColumn = rows.getColumnIndexOrThrow(Telephony.Sms.TYPE)

        buildList {
            var checkedMessages = 0

            while (checkedMessages < 100 && rows.moveToNext()) {
                checkedMessages++

                val sender = rows.getString(senderColumn) ?: "Unknown"
                val body = rows.getString(bodyColumn).orEmpty()
                val type = rows.getInt(typeColumn)

                // Filter incoming messages, not the user's outgoing replies.
                val decision = if (
                    type == Telephony.Sms.MESSAGE_TYPE_INBOX
                ) {
                    SpamEngine.evaluate(
                        sender = sender,
                        blockedSenders = senderRules.blockedSenders,
                        body = body,
                        blockedPhrases = blockedPhrases,
                        allowedSenders = senderRules.allowedSenders
                    )
                } else {
                    null
                }

                val spamReason = decision
                    ?.takeIf { it.destination == MessageDestination.SPAM }
                    ?.reason

                if (!includeSpam && spamReason != null) {
                    continue
                }

                add(
                    ConversationEntry(
                        id = rows.getLong(idColumn),
                        body = body,
                        timestampMillis = rows.getLong(dateColumn),
                        type = type,
                        spamReason = spamReason
                    )
                )
            }
        }
    }
}