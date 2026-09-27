package com.securingtheinside.blocktext

import android.Manifest
import android.app.role.RoleManager
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var isDefaultSms by mutableStateOf(false)
    private var hasSmsPermission by mutableStateOf(false)
    private var isLoading by mutableStateOf(false)
    private var inboxMessages by mutableStateOf<List<InboxMessage>>(emptyList())
    private var statusText by mutableStateOf("")

    // Keep one pending refresh while a database read is running.
    // Multiple changes can be combined because each read gets current data.
    private val refreshRequests = Channel<Unit>(Channel.CONFLATED)

    private var observingSms = false

    private val smsObserver = object : ContentObserver(
        Handler(Looper.getMainLooper())
    ) {
        override fun onChange(selfChange: Boolean) {
            if (observingSms) {
                refreshInbox()
            }
        }
    }

    private val defaultSmsRequest = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshInbox()
    }

    private val smsPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        refreshInbox()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val roleManager = getSystemService(RoleManager::class.java)
        val roleAvailable =
            roleManager?.isRoleAvailable(RoleManager.ROLE_SMS) == true

        setContent {
            MaterialTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "BlockText",
                            style = MaterialTheme.typography.headlineLarge
                        )

                        Text(
                            text = if (isDefaultSms) {
                                "Default SMS app: Yes"
                            } else {
                                "Default SMS app: No"
                            }
                        )

                        if (isDefaultSms) {
                            NotificationPermissionButton()
                            PhraseRulesButton()
                        }

                        Text(
                            text = "Emulator prototype: SMS filtering, alerts, " +
                                    "and single-part replies. MMS and multipart " +
                                    "sending are not implemented.",
                            style = MaterialTheme.typography.bodySmall
                        )

                        if (!isDefaultSms) {
                            Button(
                                enabled = roleAvailable,
                                onClick = {
                                    roleManager?.let { manager ->
                                        defaultSmsRequest.launch(
                                            manager.createRequestRoleIntent(
                                                RoleManager.ROLE_SMS
                                            )
                                        )
                                    }
                                }
                            ) {
                                Text("Set as default SMS app")
                            }

                            if (!roleAvailable) {
                                Text(
                                    "This device does not support the SMS role."
                                )
                            }
                        } else if (!hasSmsPermission) {
                            Button(
                                onClick = {
                                    smsPermissionRequest.launch(
                                        Manifest.permission.READ_SMS
                                    )
                                }
                            ) {
                                Text("Allow SMS access")
                            }
                        } else {
                            Button(
                                enabled = !isLoading,
                                onClick = { refreshInbox() }
                            ) {
                                Text(
                                    if (isLoading) {
                                        "Loading..."
                                    } else {
                                        "Refresh inbox"
                                    }
                                )
                            }
                        }

                        Text(
                            text = "Messages",
                            style = MaterialTheme.typography.titleLarge
                        )

                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall
                        )

                        MessageFolders(
                            messages = inboxMessages,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }
                }
            }
        }

        lifecycleScope.launch {
            // Start when the activity is resumed.
            // Cancel this block when it is no longer resumed.
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                try {
                    // Always catch up when returning to the app.
                    refreshInbox()

                    // Process reads one at a time.
                    refreshRequests.receiveAsFlow().collect {
                        loadInbox()
                    }
                } finally {
                    stopObservingSms()
                    isLoading = false
                }
            }
        }
    }

    override fun onDestroy() {
        stopObservingSms()
        refreshRequests.close()
        super.onDestroy()
    }

    private fun refreshInbox() {
        // Request a read rather than starting overlapping database jobs.
        refreshRequests.trySend(Unit)
    }

    private suspend fun loadInbox() {
        val roleManager = getSystemService(RoleManager::class.java)

        isDefaultSms =
            roleManager?.isRoleHeld(RoleManager.ROLE_SMS) == true

        hasSmsPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (!isDefaultSms) {
            stopObservingSms()
            inboxMessages = emptyList()
            statusText = "Choose BlockText as the default SMS app to continue."
            return
        }

        if (!hasSmsPermission) {
            stopObservingSms()
            inboxMessages = emptyList()
            statusText = "Tap Allow SMS access to read saved messages."
            return
        }

        isLoading = true

        if (inboxMessages.isEmpty()) {
            statusText = "Loading saved messages..."
        }

        try {
            // Register before reading so changes during the read are noticed.
            startObservingSms()

            inboxMessages = withContext(Dispatchers.IO) {
                readInbox()
            }

            statusText = if (inboxMessages.isEmpty()) {
                "No incoming SMS messages saved yet."
            } else {
                "Incoming SMS loaded: ${inboxMessages.size} " +
                        "(up to the latest 50)."
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: SecurityException) {
            stopObservingSms()
            inboxMessages = emptyList()
            hasSmsPermission = false
            statusText = "SMS access is needed to read the inbox."
        } catch (error: Exception) {
            statusText =
                "Could not load inbox: ${error.javaClass.simpleName}"
        } finally {
            isLoading = false
        }
    }

    private fun startObservingSms() {
        if (observingSms) return

        contentResolver.registerContentObserver(
            Telephony.Sms.CONTENT_URI,
            true,
            smsObserver
        )

        observingSms = true
    }

    private fun stopObservingSms() {
        if (!observingSms) return

        // Ignore callbacks that may already be waiting on the main thread.
        observingSms = false
        contentResolver.unregisterContentObserver(smsObserver)
    }

    private fun readInbox(): List<InboxMessage> {
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY
        )

        val sortOrder =
            "${Telephony.Sms.DATE} DESC, ${Telephony.Sms._ID} DESC"

        val cursor = contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        ) ?: throw IllegalStateException("SMS database unavailable")

        return cursor.use {
            val idColumn = it.getColumnIndexOrThrow(Telephony.Sms._ID)
            val senderColumn = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyColumn = it.getColumnIndexOrThrow(Telephony.Sms.BODY)

            buildList {
                while (size < 50 && it.moveToNext()) {
                    add(
                        InboxMessage(
                            id = it.getLong(idColumn),
                            sender = it.getString(senderColumn) ?: "Unknown",
                            body = it.getString(bodyColumn).orEmpty()
                        )
                    )
                }
            }
        }
    }
}

data class InboxMessage(
    val id: Long,
    val sender: String,
    val body: String
)