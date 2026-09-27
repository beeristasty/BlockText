package com.securingtheinside.blocktext

import org.junit.Assert.assertEquals
import org.junit.Test

class SpamNumberMatchingTest {

    @Test
    fun nationalSenderMatchesInternationalBlockEntry() {
        val result = SpamEngine.classify(
            sender = "2025550101",
            blockedSenders = setOf("+12025550101")
        )

        assertEquals(MessageDestination.SPAM, result)
    }

    @Test
    fun internationalSenderMatchesOldNationalBlockEntry() {
        val result = SpamEngine.classify(
            sender = "+12025550101",
            blockedSenders = setOf("2025550101")
        )

        assertEquals(MessageDestination.SPAM, result)
    }

    @Test
    fun formattedSenderMatchesPlainBlockEntry() {
        val result = SpamEngine.classify(
            sender = "(202) 555-0101",
            blockedSenders = setOf("2025550101")
        )

        assertEquals(MessageDestination.SPAM, result)
    }

    @Test
    fun differentCountryCodeDoesNotMatch() {
        // Same final digits must not override a different country code.
        val result = SpamEngine.classify(
            sender = "+442025550101",
            blockedSenders = setOf("+12025550101")
        )

        assertEquals(MessageDestination.INBOX, result)
    }
}