package com.nothing.budget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/**
 * Listens for incoming SMS and records them in the active session.
 *
 * Two filters before recording:
 *   1. There must be an active session (no session = ignore everything).
 *   2. The SMS timestamp must be at or after the session's [Session.startedAt],
 *      so we never back-fill historical messages into a fresh session.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val storage = Storage(context)
        val session = storage.activeSession() ?: return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val settings = storage.loadSettings()

        val grouped = messages.groupBy { it.originatingAddress.orEmpty() }
        var added = false
        for ((_, parts) in grouped) {
            val body = parts.joinToString("") { it.messageBody.orEmpty() }
            val ts = parts.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()

            // Skip anything that predates the session start.
            if (ts < session.startedAt) continue

            val id = ts xor body.hashCode().toLong()
            val parsed = SmsParser.parse(body, id, ts, settings, session.id) ?: continue
            if (storage.addTransaction(parsed)) added = true
        }

        if (added) BudgetWidget.broadcastUpdate(context)
    }
}
