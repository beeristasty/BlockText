package com.securingtheinside.blocktext

import android.content.Context
import android.content.SharedPreferences

data class SenderRules(
    val blockedSenders: Set<String>,
    val allowedSenders: Set<String>
)

class BlockListStore(context: Context) {

    private val preferences = context.applicationContext.getSharedPreferences(
        "blocktext_block_list",
        Context.MODE_PRIVATE
    )

    fun getRules(): SenderRules = synchronized(writeLock) {
        SenderRules(
            blockedSenders = readSenderSet(BLOCKED_KEY),
            allowedSenders = readSenderSet(ALLOWED_KEY)
        )
    }

    fun getBlockedSenders(): Set<String> {
        return getRules().blockedSenders
    }

    fun getAllowedSenders(): Set<String> {
        return getRules().allowedSenders
    }

    fun isBlocked(sender: String): Boolean {
        return SenderNormalizer.normalize(sender) in getBlockedSenders()
    }

    fun isAllowed(sender: String): Boolean {
        return SenderNormalizer.normalize(sender) in getAllowedSenders()
    }

    // Call from a background thread.
    fun setBlocked(sender: String, blocked: Boolean) {
        require(sender.isNotBlank()) {
            "A sender is required."
        }

        val normalizedSender = SenderNormalizer.normalize(sender)

        synchronized(writeLock) {
            val blockedSenders = readSenderSet(BLOCKED_KEY).toMutableSet()
            val allowedSenders = readSenderSet(ALLOWED_KEY).toMutableSet()

            if (blocked) {
                blockedSenders.add(normalizedSender)
                allowedSenders.remove(normalizedSender)
            } else {
                blockedSenders.remove(normalizedSender)
            }

            save(blockedSenders, allowedSenders)
        }
    }

    // Call from a background thread.
    fun setAllowed(sender: String, allowed: Boolean) {
        require(sender.isNotBlank()) {
            "A sender is required."
        }

        val normalizedSender = SenderNormalizer.normalize(sender)

        synchronized(writeLock) {
            val blockedSenders = readSenderSet(BLOCKED_KEY).toMutableSet()
            val allowedSenders = readSenderSet(ALLOWED_KEY).toMutableSet()

            if (allowed) {
                allowedSenders.add(normalizedSender)
                blockedSenders.remove(normalizedSender)
            } else {
                allowedSenders.remove(normalizedSender)
            }

            save(blockedSenders, allowedSenders)
        }
    }

    fun observeChanges(onChanged: () -> Unit): () -> Unit {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener {
                _, changedKey ->
            if (
                changedKey == BLOCKED_KEY ||
                changedKey == ALLOWED_KEY ||
                changedKey == null
            ) {
                onChanged()
            }
        }

        preferences.registerOnSharedPreferenceChangeListener(listener)

        return {
            preferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    private fun readSenderSet(key: String): Set<String> {
        return preferences.getStringSet(key, emptySet())
            .orEmpty()
            .map { SenderNormalizer.normalize(it) }
            .toSet()
    }

    private fun save(
        blockedSenders: Set<String>,
        allowedSenders: Set<String>
    ) {
        // Save both lists together so the settings remain consistent.
        check(
            preferences.edit()
                .putStringSet(BLOCKED_KEY, blockedSenders)
                .putStringSet(ALLOWED_KEY, allowedSenders)
                .commit()
        ) {
            "Could not save sender settings."
        }
    }

    companion object {
        private const val BLOCKED_KEY = "blocked_senders"
        private const val ALLOWED_KEY = "allowed_senders"
        private val writeLock = Any()
    }
}