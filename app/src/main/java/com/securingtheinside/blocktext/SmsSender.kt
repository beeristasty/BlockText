package com.securingtheinside.blocktext

import android.Manifest
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.i18n.phonenumbers.PhoneNumberUtil
import android.app.role.RoleManager

object SmsSender {

    const val ACTION_SMS_SENT =
        "com.securingtheinside.blocktext.SMS_SENT"

    // Call from a background thread.
    fun submit(
        context: Context,
        recipient: String,
        body: String
    ): Uri {

        val appContext = context.applicationContext

        val roleManager = appContext.getSystemService(RoleManager::class.java)

        check(
            roleManager?.isRoleHeld(RoleManager.ROLE_SMS) == true
        ) {
            "Make BlockText the default SMS app before sending."
        }

        if (
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("SMS sending permission is missing.")
        }

        require(body.isNotBlank()) {
            "Enter a message before sending."
        }

        val destination = SenderNormalizer.normalize(recipient)

        require(
            Regex("""\+[1-9][0-9]{1,14}""").matches(destination)
        ) {
            "Replies currently require a complete phone number. " +
                    "Short codes and text sender IDs are not supported yet."
        }

        val phoneUtil = PhoneNumberUtil.getInstance()
        val parsedNumber = phoneUtil.parse(destination, "US")

        require(
            phoneUtil.isPossibleNumberWithReason(parsedNumber) ==
                    PhoneNumberUtil.ValidationResult.IS_POSSIBLE
        ) {
            "The recipient's phone number is incomplete."
        }

        // Ask Android which SMS subscription it resolves for this app.
        val defaultManager = if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        ) {
            appContext.getSystemService(SmsManager::class.java)
                ?: throw IllegalStateException("SMS service unavailable.")
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }

        val subscriptionId = defaultManager.subscriptionId

        check(SubscriptionManager.isValidSubscriptionId(subscriptionId)) {
            "Android has not resolved a usable SMS subscription. " +
                    "No SMS was submitted."
        }

// Bind this send operation to that specific subscription.
        val smsManager = if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        ) {
            defaultManager.createForSubscriptionId(subscriptionId)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
        }

        val parts = smsManager.divideMessage(body)

        require(parts.size == 1) {
            "This version sends single-part SMS only. " +
                    "Shorten the reply and try again."
        }

        val threadId = Telephony.Threads.getOrCreateThreadId(
            appContext,
            destination
        )

        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, destination)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.THREAD_ID, threadId)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_NONE)
            put(Telephony.Sms.SUBSCRIPTION_ID, subscriptionId)
        }

        // Save first. Do not send if saving fails.
        val savedMessage = appContext.contentResolver.insert(
            Telephony.Sms.Outbox.CONTENT_URI,
            values
        ) ?: throw IllegalStateException("Could not save the outgoing SMS.")

        try {
            val callbackIntent = Intent(
                appContext,
                SmsSendResultReceiver::class.java
            ).apply {
                action = ACTION_SMS_SENT
                data = savedMessage
            }

            val sentCallback = PendingIntent.getBroadcast(
                appContext,
                0,
                callbackIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )

            smsManager.sendTextMessage(
                destination,
                null,
                body,
                sentCallback,
                null
            )
        } catch (error: Exception) {
            // Submission failed before we could hand it off normally.
            try {
                val failedValues = ContentValues().apply {
                    put(
                        Telephony.Sms.TYPE,
                        Telephony.Sms.MESSAGE_TYPE_FAILED
                    )
                }

                appContext.contentResolver.update(
                    savedMessage,
                    failedValues,
                    "${Telephony.Sms.TYPE} = ?",
                    arrayOf(Telephony.Sms.MESSAGE_TYPE_OUTBOX.toString())
                )
            } catch (_: Exception) {
                Log.e(
                    "BlockText",
                    "Could not record failed-send status."
                )
            }

            throw error
        }

        // This means submitted to Android, not confirmed sent.
        return savedMessage
    }
}