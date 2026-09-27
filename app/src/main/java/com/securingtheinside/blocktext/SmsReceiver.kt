package com.securingtheinside.blocktext

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import java.util.concurrent.Executors

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) {
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) {
            return
        }

        val firstMessage = messages.first()

        val sender = firstMessage.displayOriginatingAddress
            ?: firstMessage.originatingAddress
            ?: "Unknown"

        val body = messages.joinToString(separator = "") {
            it.messageBody.orEmpty()
        }

        val receivedAt = System.currentTimeMillis()
        val sentAt = firstMessage.timestampMillis
        val appContext = context.applicationContext

        val pendingResult = goAsync()

        databaseExecutor.execute {
            try {
                val threadId = Telephony.Threads.getOrCreateThreadId(
                    appContext,
                    sender
                )

                val values = ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, sender)
                    put(Telephony.Sms.BODY, body)
                    put(Telephony.Sms.DATE, receivedAt)
                    put(Telephony.Sms.DATE_SENT, sentAt)
                    put(Telephony.Sms.THREAD_ID, threadId)
                    put(Telephony.Sms.READ, 0)
                    put(Telephony.Sms.SEEN, 0)
                }

                val savedMessage = appContext.contentResolver.insert(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    values
                )

                if (savedMessage == null) {
                    Log.e(TAG, "SMS was not saved: the database returned null.")
                    return@execute
                }

                Log.d(TAG, "Saved incoming SMS to Android's SMS inbox.")

                // Notification failure must not undo a saved message.
                try {
                    val senderRules = BlockListStore(appContext).getRules()
                    val blockedPhrases = PhraseRuleStore(appContext).getPhrases()

                    val destination = SpamEngine.classify(
                        sender = sender,
                        blockedSenders = senderRules.blockedSenders,
                        body = body,
                        blockedPhrases = blockedPhrases,
                        allowedSenders = senderRules.allowedSenders
                    )

                    when (destination) {
                        MessageDestination.SPAM -> {
                            Log.d(
                                TAG,
                                "Spam message: notification skipped."
                            )
                        }

                        MessageDestination.INBOX -> {
                            val submitted = SmsNotifications.show(
                                context = appContext,
                                messageUri = savedMessage
                            )

                            if (submitted) {
                                Log.d(
                                    TAG,
                                    "Allowed sender: notification submitted."
                                )
                            } else {
                                Log.d(
                                    TAG,
                                    "SMS saved; no notification posted. " +
                                            "Check notification settings."
                                )
                            }
                        }
                    }
                } catch (error: Exception) {
                    Log.e(
                        TAG,
                        "SMS saved, but notification handling failed: " +
                                error.javaClass.simpleName
                    )
                }
            } catch (error: Exception) {
                Log.e(
                    TAG,
                    "SMS save failed: ${error.javaClass.simpleName}"
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BlockText"

        private val databaseExecutor =
            Executors.newSingleThreadExecutor()
    }
}