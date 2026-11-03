package com.komanda.business.core.model

import com.squareup.moshi.JsonClass
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

typealias OrderStatus = String

object OrderStatuses {
    const val APPROVED = "approved"
    const val PREPARING = "preparing"
    const val READY = "ready"
    const val DELIVERED = "delivered"
    const val CANCELLED = "cancelled"
}

@JsonClass(generateAdapter = true)
data class CustomerInfo(
    val name: String = "Cliente",
    val email: String? = null,
    val phone: String? = null
)

@JsonClass(generateAdapter = true)
data class AdminDashboardLineOption(
    val name: String,
    val priceDelta: String = "0.00",
    val quantity: Int = 1
)

@JsonClass(generateAdapter = true)
data class AdminDashboardLine(
    val id: String,
    val name: String,
    val quantity: Int,
    val unitPrice: String,
    val lineTotal: String,
    val note: String? = null,
    val options: List<AdminDashboardLineOption> = emptyList()
)

data class KitchenOptionAggregate(
    val name: String,
    val quantity: Int
)

data class KitchenProductAggregate(
    val key: String,
    val name: String,
    val totalQuantity: Int,
    val orderCount: Int,
    val options: List<KitchenOptionAggregate> = emptyList(),
    val notes: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class AdminDashboardOrder(
    val id: String,
    val purchaseNumber: String,
    val status: OrderStatus,
    val paymentStatus: String? = null, // "pending" | "paid" | "failed" | "refunded"
    val customer: CustomerInfo,
    val notes: String? = null,
    val source: String? = null, // "mercadopago-webhook" | "admin-direct" | "mercadopago_webhook" | "admin_direct"
    val lines: List<AdminDashboardLine> = emptyList(),
    val subtotal: String = "0.00",
    val discountTotal: String = "0.00",
    val total: String = "0.00",
    val currency: String = "ARS",
    val pickupPin: String? = null,
    val approvedAt: String? = null,
    val deliveredAt: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val version: Int? = null,
    val paymentExpiresAt: String? = null,
    val tender: String? = null
) {
    val isCashPending: Boolean
        get() = status != OrderStatuses.CANCELLED &&
                status != OrderStatuses.DELIVERED &&
                paymentStatus == "pending" &&
                (tender == "cash" || source == "storefront_cash" || source == "storefront-cash")

    val isInKitchenStage: Boolean
        get() = !isCashPending &&
                paymentStatus == "paid" &&
                (status == OrderStatuses.APPROVED || status == OrderStatuses.PREPARING)

    fun minutesRemaining(nowMillis: Long = System.currentTimeMillis()): Long? {
        if (paymentExpiresAt.isNullOrBlank()) return null
        val expiryMillis = parseIsoTimestamp(paymentExpiresAt) ?: return null
        val diffMillis = expiryMillis - nowMillis
        return if (diffMillis <= 0L) 0L else ((diffMillis + 59999L) / 60000L)
    }

    val minutesRemaining: Long?
        get() = minutesRemaining(System.currentTimeMillis())

    fun isPaymentExpired(nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (paymentExpiresAt.isNullOrBlank()) return false
        val expiryMillis = parseIsoTimestamp(paymentExpiresAt) ?: return false
        return expiryMillis <= nowMillis
    }

    val isPaymentExpired: Boolean
        get() = isPaymentExpired(System.currentTimeMillis())
    fun toTicketPayload(tenantName: String = "Komanda", copies: Int? = null): TicketPayload {
        val resolvedCopies = copies ?: 1
        return TicketPayload(
            orderId = id,
            purchaseNumber = purchaseNumber,
            pickupPin = pickupPin,
            source = source ?: "admin_direct",
            copies = resolvedCopies,
            tenant = tenantName,
            customer = TicketCustomer(
                name = customer.name,
                phone = customer.phone
            ),
            notes = notes,
            currency = currency,
            approvedAt = approvedAt ?: createdAt,
            items = lines.map { line ->
                TicketItem(
                    id = line.id,
                    name = line.name,
                    quantity = line.quantity,
                    unitPrice = line.unitPrice.toDoubleOrNull() ?: 0.0,
                    lineTotal = line.lineTotal.toDoubleOrNull() ?: 0.0,
                    note = line.note,
                    options = line.options.map { opt ->
                        TicketItemOption(
                            name = opt.name,
                            priceDelta = opt.priceDelta.toDoubleOrNull() ?: 0.0,
                            quantity = opt.quantity
                        )
                    }
                )
            },
            summary = TicketSummary(
                subtotal = subtotal.toDoubleOrNull() ?: 0.0,
                discountTotal = discountTotal.toDoubleOrNull() ?: 0.0,
                total = total.toDoubleOrNull() ?: 0.0
            )
        )
    }

    companion object {
        fun sourceLabel(source: String?): String {
            if (source == "admin-direct" || source == "admin_direct") {
                return "Creado por admin"
            }
            if (source == "mercadopago-webhook" || source == "mercadopago_webhook") {
                return "Pago Mercado Pago"
            }
            if (source == "storefront_cash" || source == "storefront-cash") {
                return "Efectivo Storefront"
            }
            return "Origen no disponible"
        }

        fun statusLabel(status: OrderStatus): String {
            return when (status) {
                OrderStatuses.APPROVED, OrderStatuses.PREPARING -> "Preparando"
                OrderStatuses.READY -> "Listo para entregar"
                OrderStatuses.DELIVERED -> "Entregado"
                OrderStatuses.CANCELLED -> "Cancelado"
                else -> status
            }
        }

        fun nextStatus(status: OrderStatus): OrderStatus? {
            return when (status) {
                OrderStatuses.APPROVED -> OrderStatuses.READY
                OrderStatuses.PREPARING -> OrderStatuses.READY
                OrderStatuses.READY -> OrderStatuses.DELIVERED
                else -> null
            }
        }

        fun nextStatusLabel(status: OrderStatus): String? {
            return when (nextStatus(status)) {
                OrderStatuses.READY -> "Listo para entregar"
                OrderStatuses.DELIVERED -> "Entregado"
                else -> null
            }
        }

        fun aggregateKitchenProducts(orders: List<AdminDashboardOrder>): List<KitchenProductAggregate> {
            class MutableProductEntry(
                val key: String,
                val name: String,
                var totalQuantity: Int = 0,
                val orderIds: MutableSet<String> = mutableSetOf(),
                val options: MutableMap<String, Pair<String, Int>> = linkedMapOf(),
                val notes: MutableList<String> = mutableListOf()
            )

            val map = linkedMapOf<String, MutableProductEntry>()

            for (order in orders) {
                if (!order.isInKitchenStage) continue
                for (line in order.lines) {
                    val trimmedName = line.name.trim()
                    if (trimmedName.isEmpty() || line.quantity <= 0) continue
                    val key = trimmedName.lowercase(Locale.ROOT)

                    val entry = map.getOrPut(key) {
                        MutableProductEntry(key = key, name = trimmedName)
                    }
                    entry.totalQuantity += line.quantity
                    entry.orderIds.add(order.id)

                    val cleanNote = line.note?.trim()
                    if (!cleanNote.isNullOrEmpty()) {
                        entry.notes.add("#${order.purchaseNumber}: $cleanNote")
                    }

                    for (opt in line.options) {
                        val optName = opt.name.trim()
                        if (optName.isEmpty()) continue
                        val optKey = optName.lowercase(Locale.ROOT)
                        val optQty = line.quantity * opt.quantity.coerceAtLeast(1)
                        val existing = entry.options[optKey]
                        if (existing != null) {
                            entry.options[optKey] = existing.first to (existing.second + optQty)
                        } else {
                            entry.options[optKey] = optName to optQty
                        }
                    }
                }
            }

            return map.values
                .map { entry ->
                    KitchenProductAggregate(
                        key = entry.key,
                        name = entry.name,
                        totalQuantity = entry.totalQuantity,
                        orderCount = entry.orderIds.size,
                        options = entry.options.values
                            .map { (name, qty) -> KitchenOptionAggregate(name = name, quantity = qty) }
                            .sortedWith(compareByDescending<KitchenOptionAggregate> { it.quantity }.thenBy { it.name.lowercase(Locale.ROOT) }),
                        notes = entry.notes.toList()
                    )
                }
                .sortedWith(compareByDescending<KitchenProductAggregate> { it.totalQuantity }.thenBy { it.name.lowercase(Locale.ROOT) })
        }

        fun formatDate(value: String?): String {
            if (value.isNullOrBlank()) return "-"
            return try {
                val inFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val outFormat = SimpleDateFormat("d/M/yy, HH:mm", Locale("es", "AR")).apply {
                    timeZone = TimeZone.getTimeZone("America/Argentina/Buenos_Aires")
                }
                val cleaned = value.split(".")[0].replace("Z", "")
                val parsed = inFormat.parse(cleaned) ?: Date()
                outFormat.format(parsed)
            } catch (_: Exception) {
                value
            }
        }

        fun parseIsoTimestamp(isoDate: String?): Long? {
            if (isoDate.isNullOrBlank()) return null
            // OffsetDateTime handles both 'Z' and explicit offsets (e.g. +02:00).
            try {
                return java.time.OffsetDateTime.parse(isoDate).toInstant().toEpochMilli()
            } catch (_: Throwable) {
                // fall through
            }
            try {
                return java.time.Instant.parse(isoDate).toEpochMilli()
            } catch (_: Throwable) {
                // fall through
            }
            return try {
                val cleaned = isoDate.split(".")[0].replace("Z", "")
                val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                format.parse(cleaned)?.time
            } catch (_: Exception) {
                null
            }
        }
    }
}
