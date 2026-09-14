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
    val approvedAt: String? = null,
    val deliveredAt: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val version: Int? = null
) {
    fun toTicketPayload(tenantName: String = "Komanda", copies: Int? = null): TicketPayload {
        val resolvedCopies = copies ?: 1
        return TicketPayload(
            orderId = id,
            purchaseNumber = purchaseNumber,
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
            return "Origen no disponible"
        }

        fun statusLabel(status: OrderStatus): String {
            return when (status) {
                OrderStatuses.APPROVED -> "Aprobado"
                OrderStatuses.PREPARING -> "En preparación"
                OrderStatuses.READY -> "Listo"
                OrderStatuses.DELIVERED -> "Entregado"
                OrderStatuses.CANCELLED -> "Cancelado"
                else -> status
            }
        }

        fun nextStatus(status: OrderStatus): OrderStatus? {
            return when (status) {
                OrderStatuses.APPROVED -> OrderStatuses.PREPARING
                OrderStatuses.PREPARING -> OrderStatuses.READY
                OrderStatuses.READY -> OrderStatuses.DELIVERED
                else -> null
            }
        }

        fun nextStatusLabel(status: OrderStatus): String? {
            return when (nextStatus(status)) {
                OrderStatuses.PREPARING -> "Preparar"
                OrderStatuses.READY -> "Marcar listo"
                OrderStatuses.DELIVERED -> "Marcar entregado"
                else -> null
            }
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
    }
}
