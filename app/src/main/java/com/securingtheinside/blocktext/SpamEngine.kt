package com.securingtheinside.blocktext

enum class MessageDestination {
    INBOX,
    SPAM
}

data class SpamDecision(
    val destination: MessageDestination,
    val reason: String
)

object SpamEngine {

    // Keeps existing callers compatible while supporting the new rules.
    fun classify(
        sender: String,
        blockedSenders: Set<String>,
        body: String = "",
        blockedPhrases: Set<String> = emptySet(),
        allowedSenders: Set<String> = emptySet()
    ): MessageDestination {
        return evaluate(
            sender = sender,
            blockedSenders = blockedSenders,
            body = body,
            blockedPhrases = blockedPhrases,
            allowedSenders = allowedSenders
        ).destination
    }

    fun evaluate(
        sender: String,
        blockedSenders: Set<String>,
        body: String = "",
        blockedPhrases: Set<String> = emptySet(),
        allowedSenders: Set<String> = emptySet()
    ): SpamDecision {
        val normalizedSender = SenderNormalizer.normalize(sender)

        val explicitlyBlocked = blockedSenders.any {
            SenderNormalizer.normalize(it) == normalizedSender
        }

        if (explicitlyBlocked) {
            return SpamDecision(
                destination = MessageDestination.SPAM,
                reason = "Sender is on your block list."
            )
        }

        val explicitlyAllowed = allowedSenders.any {
            SenderNormalizer.normalize(it) == normalizedSender
        }

        if (explicitlyAllowed) {
            return SpamDecision(
                destination = MessageDestination.INBOX,
                reason = "Sender is always allowed."
            )
        }

        // Sorting makes the displayed reason consistent if several rules match.
        val matchedPhrase = blockedPhrases.sorted().firstOrNull {
            PhraseMatcher.matches(body, it)
        }

        if (matchedPhrase != null) {
            return SpamDecision(
                destination = MessageDestination.SPAM,
                reason = "Matched phrase: $matchedPhrase"
            )
        }

        return SpamDecision(
            destination = MessageDestination.INBOX,
            reason = "No blocking rule matched."
        )
    }
}