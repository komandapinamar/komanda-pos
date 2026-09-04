package com.komanda.business.hardware.printing

import com.komanda.business.core.model.TicketCustomer
import com.komanda.business.core.model.TicketItem
import com.komanda.business.core.model.TicketItemOption
import com.komanda.business.core.model.TicketPayload
import com.komanda.business.core.model.TicketSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EscPosTicketRendererTest {

    @Test
    fun `formatMoney formats ARS integers without decimals`() {
        val result = EscPosTicketRenderer.formatMoney(12500.0, "ARS")
        assertEquals("$12.500", result)
    }

    @Test
    fun `formatMoney formats ARS with cents correctly`() {
        val result = EscPosTicketRenderer.formatMoney(12500.50, "ARS")
        assertEquals("$12.500,50", result)
    }

    @Test
    fun `getCopyLabel returns COCINA and CAJA for admin direct order`() {
        assertEquals("COCINA", EscPosTicketRenderer.getCopyLabel("admin_direct", 0, 2))
        assertEquals("CAJA / ENTREGA", EscPosTicketRenderer.getCopyLabel("admin_direct", 1, 2))
    }

    @Test
    fun `getStatusLabel differentiates admin direct from online payment`() {
        assertEquals("COBRAR EN CAJA", EscPosTicketRenderer.getStatusLabel("admin_direct"))
        assertEquals("PAGADO", EscPosTicketRenderer.getStatusLabel("mercadopago_webhook"))
    }

    @Test
    fun `renderTicket produces valid ESC POS bytes with headers and cut`() {
        val payload = TicketPayload(
            orderId = "ord_test_123",
            purchaseNumber = "42",
            source = "admin_direct",
            copies = 1,
            tenant = "El Noble",
            customer = TicketCustomer(name = "Juan Perez", phone = "1122334455"),
            items = listOf(
                TicketItem(
                    id = "item_1",
                    name = "Hamburguesa Doble",
                    quantity = 2,
                    unitPrice = 4500.0,
                    lineTotal = 9000.0,
                    options = listOf(
                        TicketItemOption(name = "Papas Fritas Grandes", priceDelta = 500.0)
                    )
                )
            ),
            summary = TicketSummary(
                subtotal = 9000.0,
                total = 9000.0
            )
        )

        val bytes = EscPosTicketRenderer.renderTicket(payload)
        assertTrue("Output bytes should not be empty", bytes.isNotEmpty())

        // Check for ESC @ (0x1B, 0x40) init command
        assertEquals(0x1B.toByte(), bytes[0])
        assertEquals(0x40.toByte(), bytes[1])

        // Verify string content in output (Latin1 decoded)
        val text = String(bytes, Charsets.ISO_8859_1)
        assertTrue(text.contains("EL NOBLE"))
        assertTrue(text.contains("COCINA"))
        assertTrue(text.contains("Compra #42"))
        assertTrue(text.contains("Juan Perez"))
        assertTrue(text.contains("2 x Hamburguesa Doble"))
        assertTrue(text.contains("+ Papas Fritas Grandes"))
        assertTrue(text.contains("$9.000"))
    }
}
