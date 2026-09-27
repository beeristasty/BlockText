package com.securingtheinside.blocktext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun MessageFolders(
    messages: List<InboxMessage>,
    modifier: Modifier = Modifier
) {
    val appContext = LocalContext.current.applicationContext

    val senderStore = remember(appContext) {
        BlockListStore(appContext)
    }

    val phraseStore = remember(appContext) {
        PhraseRuleStore(appContext)
    }

    var senderRules by remember(senderStore) {
        mutableStateOf(senderStore.getRules())
    }

    var blockedPhrases by remember(phraseStore) {
        mutableStateOf(phraseStore.getPhrases())
    }

    var selectedFolder by remember {
        mutableStateOf(MessageDestination.INBOX)
    }

    DisposableEffect(senderStore, phraseStore) {
        val stopWatchingSenders = senderStore.observeChanges {
            senderRules = senderStore.getRules()
        }

        val stopWatchingPhrases = phraseStore.observeChanges {
            blockedPhrases = phraseStore.getPhrases()
        }

        senderRules = senderStore.getRules()
        blockedPhrases = phraseStore.getPhrases()

        onDispose {
            stopWatchingSenders()
            stopWatchingPhrases()
        }
    }

    val (spamMessages, inboxMessages) = remember(
        messages,
        senderRules,
        blockedPhrases
    ) {
        messages.map { message ->
            val decision = SpamEngine.evaluate(
                sender = message.sender,
                blockedSenders = senderRules.blockedSenders,
                body = message.body,
                blockedPhrases = blockedPhrases,
                allowedSenders = senderRules.allowedSenders
            )

            message to decision
        }.partition { classifiedMessage ->
            classifiedMessage.second.destination == MessageDestination.SPAM
        }
    }

    val visibleMessages = when (selectedFolder) {
        MessageDestination.INBOX -> inboxMessages
        MessageDestination.SPAM -> spamMessages
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFolder == MessageDestination.INBOX,
                onClick = {
                    selectedFolder = MessageDestination.INBOX
                },
                label = {
                    Text("Inbox (${inboxMessages.size})")
                }
            )

            FilterChip(
                selected = selectedFolder == MessageDestination.SPAM,
                onClick = {
                    selectedFolder = MessageDestination.SPAM
                },
                label = {
                    Text("Spam (${spamMessages.size})")
                }
            )
        }

        if (selectedFolder == MessageDestination.SPAM) {
            Text(
                text = "Use Always allow to restore a sender and exempt " +
                        "them from phrase rules.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (visibleMessages.isEmpty()) {
            Text(
                text = when (selectedFolder) {
                    MessageDestination.INBOX -> "No messages in Inbox."
                    MessageDestination.SPAM -> "No messages in Spam."
                }
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = visibleMessages,
                key = { classifiedMessage -> classifiedMessage.first.id }
            ) { (message, decision) ->
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = message.sender,
                            style = MaterialTheme.typography.titleMedium
                        )

                        Text(
                            text = message.body,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        if (decision.destination == MessageDestination.SPAM) {
                            Text(
                                text = decision.reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        ConversationButton(
                            message = message,
                            includeSpam = selectedFolder == MessageDestination.SPAM
                        )

                        SenderRuleButtons(sender = message.sender)
                    }
                }
            }
        }
    }
}