package com.komanda.business.features.pos

import android.content.Context
import android.content.SharedPreferences
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.security.MessageDigest
import java.util.UUID

enum class AttemptStatus {
    PENDING,
    UNKNOWN,
    CONFIRMED
}

data class CheckoutAttempt(
    val idempotencyKey: String,
    val cartHash: String,
    val status: AttemptStatus,
    val serverOrderId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

class CheckoutAttemptStore(
    context: Context,
    prefsName: String = "komanda_checkout_attempts"
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(CheckoutAttempt::class.java)

    fun getOrStartAttempt(tenantId: String, cartHash: String): CheckoutAttempt {
        val existingJson = prefs.getString("attempt_$tenantId", null)
        if (existingJson != null) {
            try {
                val existing = adapter.fromJson(existingJson)
                if (existing != null && existing.cartHash == cartHash && existing.status != AttemptStatus.CONFIRMED) {
                    return existing
                }
            } catch (_: Exception) {}
        }

        val newAttempt = CheckoutAttempt(
            idempotencyKey = UUID.randomUUID().toString(),
            cartHash = cartHash,
            status = AttemptStatus.PENDING
        )
        saveAttempt(tenantId, newAttempt)
        return newAttempt
    }

    fun markAttemptUnknown(tenantId: String, attempt: CheckoutAttempt) {
        saveAttempt(tenantId, attempt.copy(status = AttemptStatus.UNKNOWN))
    }

    fun markAttemptConfirmed(tenantId: String, attempt: CheckoutAttempt, orderId: String) {
        saveAttempt(
            tenantId,
            attempt.copy(status = AttemptStatus.CONFIRMED, serverOrderId = orderId)
        )
    }

    fun clearConfirmedAttempt(tenantId: String) {
        prefs.edit().remove("attempt_$tenantId").apply()
    }

    private fun saveAttempt(tenantId: String, attempt: CheckoutAttempt) {
        prefs.edit().putString("attempt_$tenantId", adapter.toJson(attempt)).apply()
    }

    companion object {
        fun computeCartHash(items: List<Pair<String, Int>>, customerName: String, notes: String?): String {
            val content = buildString {
                append("customer:").append(customerName.trim()).append(";")
                append("notes:").append(notes?.trim() ?: "").append(";")
                items.sortedBy { it.first }.forEach { (id, qty) ->
                    append(id).append(":").append(qty).append(";")
                }
            }
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest(content.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
