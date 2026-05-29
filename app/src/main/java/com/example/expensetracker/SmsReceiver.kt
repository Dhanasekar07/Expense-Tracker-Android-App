package com.example.expensetracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsMessage

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private val PAYMENT_KEYWORDS = listOf(
            "debited", "credited", "paid", "received", "spent",
            "sent", "withdrawn", "deposited", "transferred", "purchase",
            "₹", "rs.", "rs ", "inr"
        )

        private val IGNORE_SENDERS = setOf(
            "Google", "Amazon", "Facebook", "Twitter", "WhatsApp",
            "Instagram", "LinkedIn", "GitHub", "OTP"
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.SMS_RECEIVED") return

        val bundle = intent.extras ?: return
        val messages = mutableListOf<SmsMessage>()

        val pdus = bundle.get("pdus") as? Array<*> ?: return

        for (pdu in pdus) {
            val sms = SmsMessage.createFromPdu(
                pdu as ByteArray,
                bundle.getString("format")
            )
            messages.add(sms)
        }

        for (sms in messages) {
            val sender = sms.originatingAddress ?: continue
            val body = sms.messageBody

            if (PAYMENT_KEYWORDS.none { body.lowercase().contains(it) }) continue
            if (IGNORE_SENDERS.any { sender.contains(it, ignoreCase = true) }) continue

            val amount = extractAmount(body)
            val timestamp = sms.timestampMillis

            val deduplicator = TransactionDeduplicator(context)
            val txnHash = deduplicator.generateHash(amount, sender, timestamp / 1000)
            
            if (deduplicator.isRecentHash(txnHash)) {
                continue
            }

            deduplicator.addHash(txnHash)

            val overlayIntent = Intent(context, OverlayService::class.java).apply {
                putExtra("amount", amount)
                putExtra("source", sender)
                putExtra("snippet", body.take(60))
                putExtra("channel", "sms")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(overlayIntent)
            } else {
                context.startService(overlayIntent)
            }

            break
        }
    }

    private fun extractAmount(text: String): Double {
        val regex = Regex(
            """(?:₹|rs\.?|inr)\s*([0-9,]+(?:\.[0-9]{1,2})?)""",
            RegexOption.IGNORE_CASE
        )
        val match = regex.find(text) ?: return 0.0
        return match.groupValues[1].replace(",", "").toDoubleOrNull() ?: 0.0
    }
}
