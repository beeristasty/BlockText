package com.securingtheinside.blocktext

import org.junit.Assert.assertEquals
import org.junit.Test

class SpamEngineTest {

    @Test
    fun blockedSenderGoesToSpam() {
        val result = SpamEngine.classify(
            sender = "2025550101",
            blockedSenders = setOf("2025550101")
        )

        assertEquals(MessageDestination.SPAM, result)
    }

    @Test
    fun differentSenderStaysInInbox() {
        val result = SpamEngine.classify(
            sender = "2025550102",
            blockedSenders = setOf("2025550101")
        )

        assertEquals(MessageDestination.INBOX, result)
    }

    @Test
    fun emptyBlockListKeepsMessageInInbox() {
        val result = SpamEngine.classify(
            sender = "2025550101",
            blockedSenders = emptySet()
        )

        assertEquals(MessageDestination.INBOX, result)
    }
}