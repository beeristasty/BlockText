package com.securingtheinside.blocktext

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhraseMatcherTest {

    @Test
    fun matchesPhraseIgnoringCapitalization() {
        assertTrue(
            PhraseMatcher.matches(
                body = "Notice: UNPAID TOLL.",
                phrase = "unpaid toll"
            )
        )
    }

    @Test
    fun matchesAcrossExtraWhitespace() {
        assertTrue(
            PhraseMatcher.matches(
                body = "Notice: unpaid\n   toll.",
                phrase = "unpaid toll"
            )
        )
    }

    @Test
    fun doesNotMatchInsideLargerWords() {
        assertFalse(
            PhraseMatcher.matches(
                body = "The twins arrived.",
                phrase = "win"
            )
        )

        assertFalse(
            PhraseMatcher.matches(
                body = "Your winning numbers are ready.",
                phrase = "win"
            )
        )
    }

    @Test
    fun ignoresBlankRules() {
        assertFalse(
            PhraseMatcher.matches(
                body = "An ordinary message.",
                phrase = "   "
            )
        )
    }

    @Test
    fun treatsSpecialCharactersLiterally() {
        assertFalse(
            PhraseMatcher.matches(
                body = "aaaab",
                phrase = "a+b"
            )
        )

        assertTrue(
            PhraseMatcher.matches(
                body = "Use code a+b today.",
                phrase = "a+b"
            )
        )
    }

    @Test
    fun doesNotMatchAnUnrelatedMessage() {
        assertFalse(
            PhraseMatcher.matches(
                body = "Your appointment is tomorrow.",
                phrase = "unpaid toll"
            )
        )
    }
}