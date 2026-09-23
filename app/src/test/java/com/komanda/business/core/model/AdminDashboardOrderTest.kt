package com.komanda.business.core.model

import com.komanda.business.core.network.TenantOrderLineResponse
import com.komanda.business.core.network.TenantOrderResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdminDashboardOrderTest {

    @Test
    fun `sourceLabel returns exact label from Next js AdminOrdersLive`() {
        assertEquals("Creado por admin", AdminDashboardOrder.sourceLabel("admin-direct"))
        assertEquals("Creado por admin", AdminDashboardOrder.sourceLabel("admin_direct"))
        assertEquals("Pago Mercado Pago", AdminDashboardOrder.sourceLabel("mercadopago-webhook"))
        assertEquals("Pago Mercado Pago", AdminDashboardOrder.sourceLabel("mercadopago_webhook"))
        assertEquals("Origen no disponible", AdminDashboardOrder.sourceLabel(null))
        assertEquals("Origen no disponible", AdminDashboardOrder.sourceLabel("unknown"))
    }

    @Test
    fun `statusLabel returns exact Spanish label from Next js`() {
        assertEquals("Preparando", AdminDashboardOrder.statusLabel("approved"))
        assertEquals("Preparando", AdminDashboardOrder.statusLabel("preparing"))
        assertEquals("Listo para entregar", AdminDashboardOrder.statusLabel("ready"))
        assertEquals("Entregado", AdminDashboardOrder.statusLabel("delivered"))
        assertEquals("Cancelado", AdminDashboardOrder.statusLabel("cancelled"))
    }

    @Test
    fun `nextStatus returns correct succession from Next js`() {
        assertEquals("ready", AdminDashboardOrder.nextStatus("approved"))
        assertEquals("ready", AdminDashboardOrder.nextStatus("preparing"))
        assertEquals("delivered", AdminDashboardOrder.nextStatus("ready"))
        assertNull(AdminDashboardOrder.nextStatus("delivered"))
        assertNull(AdminDashboardOrder.nextStatus("cancelled"))
    }

    @Test
    fun `nextStatusLabel returns exact button label from Next js`() {
        assertEquals("Listo para entregar", AdminDashboardOrder.nextStatusLabel("approved"))
        assertEquals("Listo para entregar", AdminDashboardOrder.nextStatusLabel("preparing"))
        assertEquals("Entregado", AdminDashboardOrder.nextStatusLabel("ready"))
        assertNull(AdminDashboardOrder.nextStatusLabel("delivered"))
        assertNull(AdminDashboardOrder.nextStatusLabel("cancelled"))
    }

    @Test
    fun `TenantOrderResponse maps to AdminDashboardOrder matching Next js toDashboardOrder`() {
        val dto = TenantOrderResponse(
            id = "ord_100",
            purchaseNumber = 42,
            fulfillmentStatus = "approved",
            paymentStatus = "paid",
            customer = mapOf("name" to "Gonzalo Perez", "phone" to "1144556677"),
            notes = "Bien cocido",
            source = "admin_direct",
            lines = listOf(
                TenantOrderLineResponse(
                    id = "line_1",
                    name = "Hamburguesa Especial",
                    quantity = 2,
                    unitPrice = "3000.00",
                    lineTotal = "6000.00",
                    options = listOf(
                        AdminDashboardLineOption(name = "Extra Cheddar", priceDelta = "500.00", quantity = 1)
                    )
                )
            ),
            subtotal = "6000.00",
            discountTotal = "0.00",
            total = "6000.00",
            currency = "ARS",
            createdAt = "2026-09-02T18:00:00Z",
            updatedAt = "2026-09-02T18:05:00Z",
            version = 2
        )

        val order = dto.toDashboardOrder()
        assertEquals("ord_100", order.id)
        assertEquals("42", order.purchaseNumber)
        assertEquals("approved", order.status)
        assertEquals("paid", order.paymentStatus)
        assertEquals("Gonzalo Perez", order.customer.name)
        assertEquals("1144556677", order.customer.phone)
        assertEquals(1, order.lines.size)
        assertEquals(2, order.version)

        val ticketPayload = order.toTicketPayload("Komanda Pinamar")
        assertEquals(1, ticketPayload.copies)
        assertEquals("Komanda Pinamar", ticketPayload.tenant)
        assertEquals("Gonzalo Perez", ticketPayload.customer.name)
        assertEquals(1, ticketPayload.items.size)
        assertEquals(6000.0, ticketPayload.summary.total, 0.01)
    }
}
