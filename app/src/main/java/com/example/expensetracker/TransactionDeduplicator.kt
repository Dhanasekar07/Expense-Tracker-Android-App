package com.example.expensetracker

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest

class TransactionDeduplicator(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "expense_dedup",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val DEDUP_WINDOW_MS = 10_000L
        private const val PREFIX = "txn_"
    }

    fun generateHash(amount: Double, source: String, timestampSeconds: Long): String {
        val input = "$amount|$source|$timestampSeconds"
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun isRecentHash(hash: String): Boolean {
        val storedTime = prefs.getLong(PREFIX + hash, -1L)
        if (storedTime == -1L) return false

        val age = System.currentTimeMillis() - storedTime
        return age < DEDUP_WINDOW_MS
    }

    fun addHash(hash: String) {
        prefs.edit().putLong(PREFIX + hash, System.currentTimeMillis()).apply()
    }

    fun cleanup() {
        val now = System.currentTimeMillis()
        val allKeys = prefs.all.keys

        prefs.edit().apply {
            for (key in allKeys) {
                if (key.startsWith(PREFIX)) {
                    val timestamp = prefs.getLong(key, 0)
                    if (now - timestamp > DEDUP_WINDOW_MS) {
                        remove(key)
                    }
                }
            }
        }.apply()
    }
}
