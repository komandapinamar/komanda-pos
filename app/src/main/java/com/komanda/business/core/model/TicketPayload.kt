package com.komanda.business.core.model

import java.net.URI

data class TicketCustomer(
    val name: String? = null,
    val phone: String? = null,
    val address: String? = null
)

data class TicketItemOption(
    val name: String,
    val priceDelta: Double = 0.0,
    val quantity: Int = 1
)

data class TicketItem(
    val id: String,
    val name: String,
    val quantity: Int,
    val unitPrice: Double,
    val lineTotal: Double,
    val note: String? = null,
    val options: List<TicketItemOption> = emptyList()
)

data class TicketSummary(
    val subtotal: Double,
    val discountTotal: Double = 0.0,
    val total: Double
)

data class FiscalInvoiceData(
    val invoiceType: String, // "B", "A", "C"
    val pointOfSale: Int,
    val invoiceNumber: Long,
    val cae: String,
    val caeDueDate: String,
    val qrData: String? = null
)

data class TicketPayload(
    val orderId: String,
    val purchaseNumber: String? = null,
    val pickupPin: String? = null,
    val source: String = "admin_direct",
    val copies: Int = 1,
    val tenant: String = "Komanda",
    val customer: TicketCustomer = TicketCustomer(),
    val notes: String? = null,
    val currency: String = "ARS",
    val approvedAt: String? = null,
    val items: List<TicketItem> = emptyList(),
    val summary: TicketSummary = TicketSummary(0.0, 0.0, 0.0),
    val fiscalInfo: FiscalInvoiceData? = null,
    val trackingUrl: String? = null
)

fun orderTrackingUrl(baseUrl: String, tenantId: String, orderId: String): String? {
    val uuidPattern = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")
    if (!uuidPattern.matches(tenantId) || !uuidPattern.matches(orderId)) return null
    val server = try { URI(baseUrl.trim()) } catch (_: IllegalArgumentException) { return null }
    if (!server.scheme.equals("https", ignoreCase = true) || server.host.isNullOrBlank() ||
        server.userInfo != null || !server.path.isNullOrBlank() && server.path != "/" ||
        server.query != null || server.fragment != null
    ) return null
    val origin = URI("https", null, server.host, server.port, null, null, null).toASCIIString()
    return "$origin/orders/status/$tenantId/$orderId"
}
