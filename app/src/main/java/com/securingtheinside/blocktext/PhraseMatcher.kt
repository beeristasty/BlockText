package com.securingtheinside.blocktext

object PhraseMatcher {

    private val whitespace = Regex("""\s+""")

    fun matches(body: String, phrase: String): Boolean {
        val cleanedPhrase = phrase.trim()

        if (body.isEmpty() || cleanedPhrase.isEmpty()) {
            return false
        }

        // Escape user-entered text so symbols are treated literally.
        // Allow flexible whitespace between the phrase's words.
        val escapedPhrase = cleanedPhrase
            .split(whitespace)
            .joinToString("""\s+""") { word ->
                Regex.escape(word)
            }

        // Avoid matching inside a larger word or identifier.
        val pattern = Regex(
            """(?<![\p{L}\p{N}_])$escapedPhrase(?![\p{L}\p{N}_])""",
            RegexOption.IGNORE_CASE
        )

        return pattern.containsMatchIn(body)
    }
}