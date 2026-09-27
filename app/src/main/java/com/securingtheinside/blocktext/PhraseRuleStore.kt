package com.securingtheinside.blocktext

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale

class PhraseRuleStore(context: Context) {

    private val preferences = context.applicationContext.getSharedPreferences(
        "blocktext_phrase_rules",
        Context.MODE_PRIVATE
    )

    fun getPhrases(): Set<String> {
        return preferences.getStringSet(KEY, emptySet())
            .orEmpty()
            .toSet()
    }

    // Call from a background thread.
    fun addPhrase(phrase: String) {
        val cleaned = normalizePhrase(phrase)

        require(cleaned.isNotBlank()) {
            "Enter a phrase."
        }

        require(cleaned.any { it.isLetterOrDigit() }) {
            "Include at least one letter or number."
        }

        require(cleaned.length <= 100) {
            "Keep each phrase to 100 characters or fewer."
        }

        synchronized(writeLock) {
            val updated = getPhrases().toMutableSet()

            require(cleaned !in updated) {
                "That phrase is already saved."
            }

            require(updated.size < 50) {
                "This prototype supports up to 50 phrase rules."
            }

            updated.add(cleaned)
            save(updated)
        }
    }

    // Call from a background thread.
    fun removePhrase(phrase: String) {
        synchronized(writeLock) {
            val updated = getPhrases().toMutableSet()
            updated.remove(normalizePhrase(phrase))
            save(updated)
        }
    }

    fun observeChanges(onChanged: () -> Unit): () -> Unit {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener {
                _, changedKey ->
            if (changedKey == KEY || changedKey == null) {
                onChanged()
            }
        }

        preferences.registerOnSharedPreferenceChangeListener(listener)

        return {
            preferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    private fun normalizePhrase(phrase: String): String {
        return phrase.trim()
            .replace(Regex("""\s+"""), " ")
            .lowercase(Locale.ROOT)
    }

    private fun save(phrases: Set<String>) {
        check(preferences.edit().putStringSet(KEY, phrases).commit()) {
            "Could not save the phrase rules."
        }
    }

    companion object {
        private const val KEY = "phrases"
        private val writeLock = Any()
    }
}