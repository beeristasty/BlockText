package com.securingtheinside.blocktext

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil

object SenderNormalizer {

    private val phoneUtil = PhoneNumberUtil.getInstance()

    // Only try phone-number parsing for phone-like text.
    // Business names and other sender identifiers remain unchanged.
    private val phoneCharacters = Regex("""[+0-9()\s.-]+""")

    fun normalize(sender: String): String {
        val cleaned = sender.trim()

        if (!phoneCharacters.matches(cleaned)) {
            return cleaned
        }

        return try {
            val parsed = phoneUtil.parse(cleaned, "US")

            // Exclude incomplete local numbers that lack an area code.
            val possibleFullNumber =
                phoneUtil.isPossibleNumberWithReason(parsed) ==
                        PhoneNumberUtil.ValidationResult.IS_POSSIBLE

            if (possibleFullNumber) {
                phoneUtil.format(
                    parsed,
                    PhoneNumberUtil.PhoneNumberFormat.E164
                )
            } else {
                cleaned
            }
        } catch (_: NumberParseException) {
            // Preserve anything we cannot confidently parse.
            cleaned
        }
    }
}