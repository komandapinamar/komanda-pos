package com.komanda.business.hardware.printing.model

import android.content.Context
import android.content.SharedPreferences

enum class PrintStatus {
    PRINTED,
    FAILED,
    UNCERTAIN
}

data class PrintJournalRecord(
    val orderId: String,
    val destination: String,
    val status: PrintStatus,
    val timestamp: Long = System.currentTimeMillis()
)

class PrintJournalStore(
    context: Context,
    prefsName: String = "komanda_print_journal"
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    fun isDestinationPrinted(orderId: String, destination: String): Boolean {
        val status = prefs.getString(key(orderId, destination), null)
        return status == PrintStatus.PRINTED.name
    }

    fun getStatus(orderId: String, destination: String): PrintStatus? {
        val status = prefs.getString(key(orderId, destination), null) ?: return null
        return try {
            PrintStatus.valueOf(status)
        } catch (_: Exception) {
            null
        }
    }

    fun recordStatus(orderId: String, destination: String, status: PrintStatus) {
        prefs.edit().putString(key(orderId, destination), status.name).apply()
    }

    private fun key(orderId: String, destination: String): String =
        "print_${orderId}_$destination"
}
