package com.securingtheinside.blocktext

import org.junit.Assert.assertEquals
import org.junit.Test

class PhraseFilteringTest {

    @Test
    fun matchingPhraseGoesToSpam() {
        val result = SpamEngine.classify(
            sender = "2025550103",
            blockedSenders = emptySet(),
            body = "Please pay your unpaid toll.",
            blockedPhrases = setOf("unpaid toll")
        )

        assertEquals(MessageDestination.SPAM, result)
    }

    @Test
    fun unrelatedMessageStaysInInbox() {
        val result = SpamEngine.classify(
            sender = "2025550103",
            blockedSenders = emptySet(),
            body = "See you at dinner tomorrow.",
            blockedPhrases = setOf("unpaid toll")
        )

        assertEquals(MessageDestination.INBOX, result)
    }

    @Test
    fun alwaysAllowedSenderSkipsPhraseRules() {
        val result = SpamEngine.classify(
            sender = "+12025550103",
            blockedSenders = emptySet(),
            body = "Please pay your unpaid toll.",
            blockedPhrases = setOf("unpaid toll"),
            allowedSenders = setOf("2025550103")
        )

        assertEquals(MessageDestination.INBOX, result)
    }

    @Test
    fun blockedSenderDoesNotNeedAMatchingPhrase() {
        val result = SpamEngine.classify(
            sender = "+12025550103",
            blockedSenders = setOf("2025550103"),
            body = "See you at dinner tomorrow.",
            blockedPhrases = setOf("unpaid toll")
        )

        assertEquals(MessageDestination.SPAM, result)
    }

    @Test
    fun explicitBlockWinsIfBothSenderSetsContainTheNumber() {
        val result = SpamEngine.classify(
            sender = "2025550103",
            blockedSenders = setOf("+12025550103"),
            body = "An ordinary message.",
            allowedSenders = setOf("2025550103")
        )

        assertEquals(MessageDestination.SPAM, result)
    }

    @Test
    fun partialWordDoesNotTriggerFiltering() {
        val result = SpamEngine.classify(
            sender = "2025550103",
            blockedSenders = emptySet(),
            body = "The twins arrived.",
            blockedPhrases = setOf("win")
        )

        assertEquals(MessageDestination.INBOX, result)
    }

    @Test
    fun decisionExplainsWhichPhraseMatched() {
        val decision = SpamEngine.evaluate(
            sender = "2025550103",
            blockedSenders = emptySet(),
            body = "Notice: UNPAID TOLL.",
            blockedPhrases = setOf("unpaid toll")
        )

        assertEquals(MessageDestination.SPAM, decision.destination)
        assertEquals(
            "Matched phrase: unpaid toll",
            decision.reason
        )
    }
}