package com.komanda.business.core.model

data class BusinessOrderOption(
    val id: String,
    val name: String,
    val priceDelta: Double,
    val quantity: Int
)

data class BusinessOrderLine(
    val id: String,
    val name: String,
    val quantity: Int,
    val unitPrice: Double,
    val lineTotal: Double,
    val note: String? = null,
    val options: List<BusinessOrderOption> = emptyList()
)

data class BusinessOrder(
    val id: String,
    val tenantId: String,
    val locationId: String,
    val purchaseNumber: String,
    val fulfillmentStatus: FulfillmentStatus,
    val paymentStatus: String,
    val source: String,
    val customerName: String,
    val customerPhone: String? = null,
    val notes: String? = null,
    val lines: List<BusinessOrderLine> = emptyList(),
    val subtotal: Double = 0.0,
    val discountTotal: Double = 0.0,
    val total: Double = 0.0,
    val currency: String = "ARS",
    val version: Int = 1,
    val createdAt: String? = null,
    val updatedAt: String? = null
) {
    fun toTicketPayload(tenantName: String = "Komanda", copies: Int? = null): TicketPayload {
        val resolvedCopies = copies ?: 1
        return TicketPayload(
            orderId = id,
            purchaseNumber = purchaseNumber,
            source = source,
            copies = resolvedCopies,
            tenant = tenantName,
            customer = TicketCustomer(name = customerName, phone = customerPhone),
            notes = notes,
            currency = currency,
            approvedAt = createdAt,
            items = lines.map { line ->
                TicketItem(
                    id = line.id,
                    name = line.name,
                    quantity = line.quantity,
                    unitPrice = line.unitPrice,
                    lineTotal = line.lineTotal,
                    note = line.note,
                    options = line.options.map { opt ->
                        TicketItemOption(name = opt.name, priceDelta = opt.priceDelta, quantity = opt.quantity)
                    }
                )
            },
            summary = TicketSummary(
                subtotal = subtotal,
                discountTotal = discountTotal,
                total = total
            )
        )
    }
}
