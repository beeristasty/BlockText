package com.securingtheinside.blocktext

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import java.util.concurrent.Executors

class SmsSendResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != SmsSender.ACTION_SMS_SENT) {
            return
        }

        val messageUri = intent.data ?: return

        if (
            messageUri.scheme != "content" ||
            messageUri.authority != Telephony.Sms.CONTENT_URI.authority
        ) {
            return
        }

        // Capture this before onReceive returns.
        val sendResult = resultCode
        val sentSuccessfully = sendResult == Activity.RESULT_OK

        val appContext = context.applicationContext
        val pendingResult = goAsync()

        databaseExecutor.execute {
            try {
                val values = ContentValues().apply {
                    put(
                        Telephony.Sms.TYPE,
                        if (sentSuccessfully) {
                            Telephony.Sms.MESSAGE_TYPE_SENT
                        } else {
                            Telephony.Sms.MESSAGE_TYPE_FAILED
                        }
                    )
                }

                // Only finalize a message that is still in the outbox.
                val updated = appContext.contentResolver.update(
                    messageUri,
                    values,
                    "${Telephony.Sms.TYPE} = ?",
                    arrayOf(Telephony.Sms.MESSAGE_TYPE_OUTBOX.toString())
                )

                if (updated > 0) {
                    if (sentSuccessfully) {
                        Log.d("BlockText", "Outgoing SMS marked sent.")
                    } else {
                        Log.w(
                            "BlockText",
                            "Outgoing SMS failed. Android result: $sendResult"
                        )
                    }
                }
            } catch (error: Exception) {
                Log.e(
                    "BlockText",
                    "Could not save SMS send result: " +
                            error.javaClass.simpleName
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private val databaseExecutor =
            Executors.newSingleThreadExecutor()
    }
}