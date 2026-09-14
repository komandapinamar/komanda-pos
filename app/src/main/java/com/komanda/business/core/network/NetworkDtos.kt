package com.komanda.business.core.network

import com.komanda.business.core.model.BusinessOrder
import com.komanda.business.core.model.BusinessOrderLine
import com.komanda.business.core.model.BusinessOrderOption
import com.komanda.business.core.model.FulfillmentStatus
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class OrderLineOptionDto(
    val id: String = "",
    val name: String = "",
    val priceDelta: String = "0.00",
    val quantity: Int = 1
)

@JsonClass(generateAdapter = true)
data class OrderLineDto(
    val id: String = "",
    val name: String = "",
    val quantity: Int = 1,
    val unitPrice: String = "0.00",
    val lineTotal: String = "0.00",
    val note: String? = null,
    val options: List<OrderLineOptionDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class OrderDto(
    val id: String,
    val tenantId: String,
    val locationId: String,
    val purchaseNumber: String,
    val fulfillmentStatus: String,
    val paymentStatus: String,
    val source: String,
    val customer: Map<String, Any?>? = null,
    val notes: String? = null,
    val lines: List<OrderLineDto> = emptyList(),
    val subtotal: String = "0.00",
    val discountTotal: String = "0.00",
    val total: String = "0.00",
    val currency: String = "ARS",
    val version: Int = 1,
    val createdAt: String? = null,
    val updatedAt: String? = null
) {
    fun toDomain(): BusinessOrder {
        val customerName = customer?.get("name")?.toString() ?: "Sin nombre"
        val customerPhone = customer?.get("phone")?.toString()

        return BusinessOrder(
            id = id,
            tenantId = tenantId,
            locationId = locationId,
            purchaseNumber = purchaseNumber,
            fulfillmentStatus = FulfillmentStatus.fromWireValue(fulfillmentStatus),
            paymentStatus = paymentStatus,
            source = source,
            customerName = customerName,
            customerPhone = customerPhone,
            notes = notes,
            lines = lines.map { line ->
                BusinessOrderLine(
                    id = line.id,
                    name = line.name,
                    quantity = line.quantity,
                    unitPrice = line.unitPrice.toDoubleOrNull() ?: 0.0,
                    lineTotal = line.lineTotal.toDoubleOrNull() ?: 0.0,
                    note = line.note,
                    options = line.options.map { opt ->
                        BusinessOrderOption(
                            id = opt.id,
                            name = opt.name,
                            priceDelta = opt.priceDelta.toDoubleOrNull() ?: 0.0,
                            quantity = opt.quantity
                        )
                    }
                )
            },
            subtotal = subtotal.toDoubleOrNull() ?: 0.0,
            discountTotal = discountTotal.toDoubleOrNull() ?: 0.0,
            total = total.toDoubleOrNull() ?: 0.0,
            currency = currency,
            version = version,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}

@JsonClass(generateAdapter = true)
data class OrdersPageResponse(
    val orders: List<OrderDto> = emptyList(),
    val nextCursor: String? = null
)

@JsonClass(generateAdapter = true)
data class OrderEventDto(
    val id: String,
    val orderId: String,
    val sequence: String,
    val eventType: String,
    val fromStatus: String? = null,
    val toStatus: String? = null,
    val occurredAt: String
)

@JsonClass(generateAdapter = true)
data class TransitionOrderRequest(
    val fulfillmentStatus: String
)

@JsonClass(generateAdapter = true)
data class DirectOrderItemRequest(
    val kind: String = "item",
    val resourceId: String,
    val quantity: Int
)

@JsonClass(generateAdapter = true)
data class DirectOrderCustomerRequest(
    val name: String,
    val phone: String? = null
)

@JsonClass(generateAdapter = true)
data class CreateDirectOrderRequest(
    val items: List<DirectOrderItemRequest>,
    val customer: DirectOrderCustomerRequest,
    val notes: String? = null
)

@JsonClass(generateAdapter = true)
data class CatalogCategoryDto(
    val id: String,
    val name: String,
    val description: String? = null
)

@JsonClass(generateAdapter = true)
data class CatalogItemDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val price: String,
    val categoryId: String? = null,
    val barcode: String? = null,
    val isGeneric: Boolean = false,
    val genericIcon: String? = null,
    val trackStock: Boolean = false,
    val stockQuantity: Int = 0,
    val status: String = "active"
)

@JsonClass(generateAdapter = true)
data class CreateCatalogItemRequest(
    val categoryId: String,
    val name: String,
    val price: String,
    val currency: String = "ARS",
    val barcode: String? = null,
    val isGeneric: Boolean = false,
    val genericIcon: String? = null,
    val trackStock: Boolean = false,
    val stockQuantity: Int = 0,
    val status: String = "active"
)

@JsonClass(generateAdapter = true)
data class BarcodeSuggestionDto(
    val name: String,
    val suggestedCategory: String? = null,
    val imageUrl: String? = null,
    val brand: String? = null
)

@JsonClass(generateAdapter = true)
data class BarcodeLookupResponseDto(
    val source: String, // "tenant", "global", "external", "none"
    val item: CatalogItemDto? = null,
    val suggestion: BarcodeSuggestionDto? = null
)

@JsonClass(generateAdapter = true)
data class CatalogResponse<T>(
    val data: List<T>
)

@JsonClass(generateAdapter = true)
data class CreateInvoiceRequest(
    val invoiceType: String = "B", // "A", "B", "C"
    val docType: String = "DNI", // "DNI", "CUIT", "FINAL"
    val docNumber: String? = null
)

@JsonClass(generateAdapter = true)
data class InvoiceResponse(
    val invoiceType: String,
    val pointOfSale: Int,
    val invoiceNumber: Long,
    val cae: String,
    val caeDueDate: String,
    val qrData: String
)

@JsonClass(generateAdapter = true)
data class CashShiftDto(
    val id: String,
    val tenantId: String,
    val openingBalance: String,
    val closingBalance: String? = null,
    val expectedCash: String? = null,
    val currentCashSales: String? = null,
    val orderCount: Int = 0,
    val status: String, // "open" | "closed"
    val openedAt: String,
    val closedAt: String? = null,
    val notes: String? = null
)

@JsonClass(generateAdapter = true)
data class CashShiftResponse(
    val data: CashShiftDto?
)

@JsonClass(generateAdapter = true)
data class OpenCashShiftRequest(
    val openingBalance: String,
    val notes: String? = null
)

@JsonClass(generateAdapter = true)
data class CloseCashShiftRequest(
    val closingBalance: String,
    val notes: String? = null
)
