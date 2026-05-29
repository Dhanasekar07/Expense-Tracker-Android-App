package com.example.expensetracker

import android.app.Notification
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.content.Intent

class PaymentNotificationListener : NotificationListenerService() {

    companion object {
        private val PAYMENT_PACKAGES = setOf(
            "com.google.android.apps.nbu.paisa.user",
            "com.google.android.apps.walletnfcrel",
            "com.phonepe.app",
            "net.one97.paytm",
            "in.org.npci.upiapp",
            "in.amazon.mShop.android.shopping",
            "com.csam.icici.bank.imobile",
            "com.sbi.lotusintouch",
            "com.snapwork.hdfc",
            "com.axis.mobile",
            "com.msf.kbank.mobile"
        )

        private val KEYWORDS = listOf(
            "debited", "credited", "paid", "received", "spent",
            "sent", "₹", "rs.", "rs ", "inr", "txn", "transaction"
        )

        @Volatile private var lastTriggerKey: String? = null
        @Volatile private var lastTriggerTime: Long = 0L
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (sbn.packageName !in PAYMENT_PACKAGES) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val combined = "$title $text $bigText".lowercase()

        if (KEYWORDS.none { combined.contains(it) }) return

        val key = "${sbn.packageName}|${sbn.id}|$title|$text"
        val now = System.currentTimeMillis()
        if (key == lastTriggerKey && now - lastTriggerTime < 3000) return
        lastTriggerKey = key
        lastTriggerTime = now

        val amount = extractAmount(combined)
        val timestamp = System.currentTimeMillis()

        val deduplicator = TransactionDeduplicator(this)
        val txnHash = deduplicator.generateHash(amount, sbn.packageName, timestamp / 1000)
        
        if (deduplicator.isRecentHash(txnHash)) {
            return
        }

        deduplicator.addHash(txnHash)

        val intent = Intent(this, OverlayService::class.java).apply {
            putExtra("amount", amount)
            putExtra("source", sbn.packageName)
            putExtra("snippet", text.ifBlank { title })
            putExtra("channel", "notification")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
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
