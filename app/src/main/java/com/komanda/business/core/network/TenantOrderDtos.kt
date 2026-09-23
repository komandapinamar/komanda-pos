package com.komanda.business.core.network

import com.komanda.business.core.model.AdminDashboardLine
import com.komanda.business.core.model.AdminDashboardLineOption
import com.komanda.business.core.model.AdminDashboardOrder
import com.komanda.business.core.model.CustomerInfo
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TenantOrderLineResponse(
    val id: String = "",
    val name: String = "",
    val quantity: Int = 1,
    val unitPrice: String = "0.00",
    val lineTotal: String = "0.00",
    val note: String? = null,
    val options: List<AdminDashboardLineOption> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TenantOrderResponse(
    val id: String,
    val purchaseNumber: Any, // Can be string or number in JSON
    val fulfillmentStatus: String,
    val paymentStatus: String = "pending",
    val customer: Map<String, Any?>? = null,
    val notes: String? = null,
    val source: String? = null,
    val lines: List<TenantOrderLineResponse> = emptyList(),
    val subtotal: String = "0.00",
    val discountTotal: String = "0.00",
    val total: String = "0.00",
    val currency: String = "ARS",
    val approvedAt: String? = null,
    val deliveredAt: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val version: Int = 1
) {
    fun toDashboardOrder(): AdminDashboardOrder {
        val custName = customer?.get("name")?.toString() ?: "Cliente"
        val custPhone = customer?.get("phone")?.toString()
        val custEmail = customer?.get("email")?.toString()

        return AdminDashboardOrder(
            id = id,
            purchaseNumber = purchaseNumber.toString(),
            status = fulfillmentStatus,
            paymentStatus = paymentStatus,
            customer = CustomerInfo(name = custName, email = custEmail, phone = custPhone),
            notes = notes,
            source = source,
            lines = lines.map { line ->
                AdminDashboardLine(
                    id = line.id,
                    name = line.name,
                    quantity = line.quantity,
                    unitPrice = line.unitPrice,
                    lineTotal = line.lineTotal,
                    note = line.note,
                    options = line.options
                )
            },
            subtotal = subtotal,
            discountTotal = discountTotal,
            total = total,
            currency = currency,
            approvedAt = approvedAt,
            deliveredAt = deliveredAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
            version = version
        )
    }
}

@JsonClass(generateAdapter = true)
data class TenantOrdersListResponse(
    val data: List<TenantOrderResponse> = emptyList(),
    val nextCursor: String? = null
)

@JsonClass(generateAdapter = true)
data class TenantOrderEvent(
    val orderId: String,
    val sequence: String
)

@JsonClass(generateAdapter = true)
data class TenantOrderResetEvent(
    val reason: String? = null,
    val headSequence: String? = null
)
