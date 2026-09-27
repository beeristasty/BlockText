package com.securingtheinside.blocktext

import org.junit.Assert.assertEquals
import org.junit.Test

class SenderNormalizerTest {

    @Test
    fun usNumberFormatsProduceTheSameValue() {
        val formats = listOf(
            "2025550101",
            "(202) 555-0101",
            "1-202-555-0101",
            "+1 202 555 0101"
        )

        formats.forEach { sender ->
            assertEquals(
                "+12025550101",
                SenderNormalizer.normalize(sender)
            )
        }
    }

    @Test
    fun internationalNumberKeepsItsCountryCode() {
        assertEquals(
            "+442079460018",
            SenderNormalizer.normalize("+44 20 7946 0018")
        )
    }

    @Test
    fun shortCodeRemainsUnchanged() {
        assertEquals(
            "12345",
            SenderNormalizer.normalize("12345")
        )
    }

    @Test
    fun textSenderIdRemainsUnchanged() {
        assertEquals(
            "ACMEBANK",
            SenderNormalizer.normalize("ACMEBANK")
        )
    }

    @Test
    fun sevenDigitNumberDoesNotGetAnInventedAreaCode() {
        assertEquals(
            "5550101",
            SenderNormalizer.normalize("5550101")
        )
    }

    @Test
    fun normalizingAgainDoesNotChangeTheResult() {
        val firstResult = SenderNormalizer.normalize("(202) 555-0101")

        assertEquals(
            firstResult,
            SenderNormalizer.normalize(firstResult)
        )
    }
}