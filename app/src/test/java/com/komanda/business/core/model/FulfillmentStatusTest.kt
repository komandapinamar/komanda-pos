package com.komanda.business.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FulfillmentStatusTest {

    @Test
    fun `valid transition from approved to preparing`() {
        assertTrue(FulfillmentStatus.APPROVED.canTransitionTo(FulfillmentStatus.PREPARING))
        assertEquals(FulfillmentStatus.PREPARING, FulfillmentStatus.APPROVED.nextStatus())
    }

    @Test
    fun `valid transition from preparing to ready`() {
        assertTrue(FulfillmentStatus.PREPARING.canTransitionTo(FulfillmentStatus.READY))
        assertEquals(FulfillmentStatus.READY, FulfillmentStatus.PREPARING.nextStatus())
    }

    @Test
    fun `valid transition from ready to delivered`() {
        assertTrue(FulfillmentStatus.READY.canTransitionTo(FulfillmentStatus.DELIVERED))
        assertEquals(FulfillmentStatus.DELIVERED, FulfillmentStatus.READY.nextStatus())
    }

    @Test
    fun `delivered and cancelled are terminal states`() {
        assertTrue(FulfillmentStatus.DELIVERED.isTerminal())
        assertTrue(FulfillmentStatus.CANCELLED.isTerminal())
        assertNull(FulfillmentStatus.DELIVERED.nextStatus())
        assertNull(FulfillmentStatus.CANCELLED.nextStatus())
    }

    @Test
    fun `cannot transition directly from approved to delivered`() {
        assertFalse(FulfillmentStatus.APPROVED.canTransitionTo(FulfillmentStatus.DELIVERED))
    }

    @Test
    fun `toTicketPayload defaults to one copy unless specified`() {
        val order = BusinessOrder(
            id = "ord_1",
            tenantId = "ten_1",
            locationId = "loc_1",
            purchaseNumber = "101",
            fulfillmentStatus = FulfillmentStatus.APPROVED,
            paymentStatus = "paid",
            source = "admin_direct",
            customerName = "Matias",
            total = 5000.0
        )
        val payload = order.toTicketPayload("Test Bar")
        assertEquals(1, payload.copies)
        assertEquals("101", payload.purchaseNumber)
        assertEquals("Test Bar", payload.tenant)

        val twoCopies = order.toTicketPayload("Test Bar", copies = 2)
        assertEquals(2, twoCopies.copies)
    }
}
