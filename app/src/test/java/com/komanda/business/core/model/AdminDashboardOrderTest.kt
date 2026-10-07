package com.komanda.business.core.model

import com.komanda.business.core.network.TenantOrderLineResponse
import com.komanda.business.core.network.TenantOrderResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminDashboardOrderTest {

    @Test
    fun `sourceLabel returns exact label from Next js AdminOrdersLive`() {
        assertEquals("Creado por admin", AdminDashboardOrder.sourceLabel("admin-direct"))
        assertEquals("Creado por admin", AdminDashboardOrder.sourceLabel("admin_direct"))
        assertEquals("Pago Mercado Pago", AdminDashboardOrder.sourceLabel("mercadopago-webhook"))
        assertEquals("Pago Mercado Pago", AdminDashboardOrder.sourceLabel("mercadopago_webhook"))
        assertEquals("Efectivo Storefront", AdminDashboardOrder.sourceLabel("storefront_cash"))
        assertEquals("Efectivo Storefront", AdminDashboardOrder.sourceLabel("storefront-cash"))
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

    @Test
    fun `isCashPending identifies pending cash orders correctly`() {
        val baseOrder = AdminDashboardOrder(
            id = "ord_cash_1",
            purchaseNumber = "101",
            status = "approved",
            paymentStatus = "pending",
            customer = CustomerInfo(name = "Cliente Efectivo"),
            source = "storefront_cash",
            tender = "cash",
            createdAt = "2026-10-05T12:00:00Z",
            updatedAt = "2026-10-05T12:00:00Z"
        )
        assertTrue(baseOrder.isCashPending)

        // tender cash with another source (e.g. direct order) pending
        val directCashPending = baseOrder.copy(source = "admin_direct", tender = "cash", paymentStatus = "pending")
        assertTrue(directCashPending.isCashPending)

        // storefront_cash source even if tender is omitted
        val storefrontCashNullTender = baseOrder.copy(source = "storefront_cash", tender = null, paymentStatus = "pending")
        assertTrue(storefrontCashNullTender.isCashPending)

        // paid order is NOT cash pending
        val paidOrder = baseOrder.copy(paymentStatus = "paid")
        assertFalse(paidOrder.isCashPending)

        // posnet pending is NOT cash pending
        val posnetOrder = baseOrder.copy(source = "admin_direct", tender = "posnet", paymentStatus = "pending")
        assertFalse(posnetOrder.isCashPending)

        // cancelled order is NOT cash pending
        val cancelledOrder = baseOrder.copy(status = "cancelled", paymentStatus = "pending", tender = "cash")
        assertFalse(cancelledOrder.isCashPending)

        // delivered order is NOT cash pending
        val deliveredOrder = baseOrder.copy(status = "delivered", paymentStatus = "pending", tender = "cash")
        assertFalse(deliveredOrder.isCashPending)
    }

    @Test
    fun `minutesRemaining and isPaymentExpired calculate time against paymentExpiresAt`() {
        // Base epoch: 2026-10-05T12:00:00.000Z = 1791201600000L
        val nowMillis = 1791201600000L
        // 45 minutes in future: 2026-10-05T12:45:00.000Z
        val futureExpiresAt = "2026-10-05T12:45:00.000Z"
        // 5 minutes in past: 2026-10-05T11:55:00.000Z
        val pastExpiresAt = "2026-10-05T11:55:00.000Z"

        val activeOrder = AdminDashboardOrder(
            id = "ord_exp_1",
            purchaseNumber = "102",
            status = "approved",
            paymentStatus = "pending",
            customer = CustomerInfo(),
            paymentExpiresAt = futureExpiresAt,
            createdAt = "2026-10-05T12:00:00Z",
            updatedAt = "2026-10-05T12:00:00Z"
        )

        assertEquals(45L, activeOrder.minutesRemaining(nowMillis))
        assertFalse(activeOrder.isPaymentExpired(nowMillis))

        val expiredOrder = activeOrder.copy(paymentExpiresAt = pastExpiresAt)
        assertEquals(0L, expiredOrder.minutesRemaining(nowMillis))
        assertTrue(expiredOrder.isPaymentExpired(nowMillis))

        val nullExpiryOrder = activeOrder.copy(paymentExpiresAt = null)
        assertNull(nullExpiryOrder.minutesRemaining(nowMillis))
        assertFalse(nullExpiryOrder.isPaymentExpired(nowMillis))
    }

    @Test
    fun `parseIsoTimestamp handles explicit non-UTC offsets and malformed input`() {
        // 2026-10-05T14:00:00+02:00 === 2026-10-05T12:00:00Z
        assertEquals(
            1791201600000L,
            AdminDashboardOrder.parseIsoTimestamp("2026-10-05T14:00:00+02:00"),
        )
        // Equivalent instant with Z
        assertEquals(
            1791201600000L,
            AdminDashboardOrder.parseIsoTimestamp("2026-10-05T12:00:00Z"),
        )
        assertNull(AdminDashboardOrder.parseIsoTimestamp("not-a-date"))
        assertNull(AdminDashboardOrder.parseIsoTimestamp(null))
    }

    @Test
    fun `TenantOrderResponse maps paymentExpiresAt and tender to AdminDashboardOrder`() {
        val dto = TenantOrderResponse(
            id = "ord_storefront_cash",
            purchaseNumber = "1084",
            fulfillmentStatus = "approved",
            paymentStatus = "pending",
            source = "storefront_cash",
            tender = "cash",
            paymentExpiresAt = "2026-10-05T13:00:00.000Z",
            pickupPin = "5821",
            customer = mapOf("name" to "Sofia Rodriguez"),
            createdAt = "2026-10-05T12:00:00.000Z",
            updatedAt = "2026-10-05T12:00:00.000Z"
        )

        val order = dto.toDashboardOrder()
        assertEquals("ord_storefront_cash", order.id)
        assertEquals("1084", order.purchaseNumber)
        assertEquals("approved", order.status)
        assertEquals("pending", order.paymentStatus)
        assertEquals("storefront_cash", order.source)
        assertEquals("cash", order.tender)
        assertEquals("2026-10-05T13:00:00.000Z", order.paymentExpiresAt)
        assertEquals("5821", order.pickupPin)
        assertTrue(order.isCashPending)
        assertEquals("Efectivo Storefront", AdminDashboardOrder.sourceLabel(order.source))
    }
}
