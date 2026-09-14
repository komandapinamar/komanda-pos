package com.komanda.business.hardware.printing

import com.komanda.business.core.model.TicketCustomer
import com.komanda.business.core.model.TicketItem
import com.komanda.business.core.model.TicketItemOption
import com.komanda.business.core.model.TicketPayload
import com.komanda.business.core.model.TicketSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EscPosTicketRendererTest {

    private val samplePayload = TicketPayload(
        orderId = "uuid-internal-1234-5678",
        purchaseNumber = "42",
        source = "admin_direct",
        copies = 1,
        tenant = "La Pizzería",
        customer = TicketCustomer(name = "Juan Perez", phone = "1122334455"),
        items = listOf(
            TicketItem(
                id = "item_1",
                name = "Pizza Especial",
                quantity = 2,
                unitPrice = 4500.0,
                lineTotal = 9000.0,
                options = listOf(
                    TicketItemOption(name = "Extra Queso", priceDelta = 500.0)
                )
            )
        ),
        summary = TicketSummary(
            subtotal = 9000.0,
            total = 9500.0
        )
    )

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
    fun `renderKitchenTicket outputs operational focus without prices or internal UUID`() {
        val bytes = EscPosTicketRenderer.renderKitchenTicket(samplePayload)
        val text = String(bytes, Charsets.ISO_8859_1)

        // Header assertions
        assertTrue("Must include KOMANDA primary title", text.contains("KOMANDA"))
        assertTrue("Must include restaurant name", text.contains("LA PIZZERÍA"))
        assertTrue("Must include kitchen role subtitle", text.contains("--- COCINA ---"))
        assertTrue("Must include purchase number", text.contains("Compra #42"))

        // Operational content
        assertTrue("Must include customer name", text.contains("Juan Perez"))
        assertTrue("Must include items and quantities", text.contains("2 x Pizza Especial"))
        assertTrue("Must include options", text.contains("+ Extra Queso"))
        assertTrue("Must include physical total units", text.contains("Total unidades: 2"))

        // Strict exclusions: No prices, no monetary symbols, no internal UUID
        assertFalse("Must not include dollar sign in kitchen ticket", text.contains("$"))
        assertFalse("Must not include price amounts in kitchen ticket", text.contains("9.000") || text.contains("9.500"))
        assertFalse("Must not include internal UUID in kitchen ticket", text.contains("uuid-internal-1234-5678"))
    }

    @Test
    fun `renderCounterTicket outputs commercial receipt with prices totals and ASCII art`() {
        val bytes = EscPosTicketRenderer.renderCounterTicket(samplePayload)
        val text = String(bytes, Charsets.ISO_8859_1)

        // Header assertions
        assertTrue("Must include KOMANDA primary title", text.contains("KOMANDA"))
        assertTrue("Must include restaurant name", text.contains("LA PIZZERÍA"))
        assertTrue("Must include purchase number", text.contains("Compra #42"))

        // Commercial details
        assertTrue("Must include customer name", text.contains("Juan Perez"))
        assertTrue("Must include items", text.contains("2 x Pizza Especial"))
        assertTrue("Must include line total price", text.contains("$9.000"))
        assertTrue("Must include grand total", text.contains("Total: $9.500"))
        assertTrue("Must include payment status", text.contains("COBRAR EN CAJA"))

        // ASCII art & branding
        assertTrue("Must include ASCII art cloche/food emblem", text.contains(".------.") && text.contains("| ~~~~ |"))
        assertTrue("Must include thank you message", text.contains("¡Gracias por tu compra!"))
    }

    @Test
    fun `renderEspressoCashTicket outputs prominent cash payment notice`() {
        val bytes = EscPosTicketRenderer.renderEspressoCashTicket(samplePayload)
        val text = String(bytes, Charsets.ISO_8859_1)

        assertTrue("Must include KOMANDA header", text.contains("KOMANDA"))
        assertTrue("Must include AUTOSERVICIO EXPRESS subtitle", text.contains("AUTOSERVICIO EXPRESS"))
        assertTrue("Must include items", text.contains("2 x Pizza Especial"))
        assertTrue("Must include total to pay", text.contains("TOTAL A PAGAR: $9.500"))
        assertTrue("Must include attention notice", text.contains("*** ATENCION ***"))
        assertTrue("Must include cash instruction", text.contains("ACERCARSE A CAJA"))
        assertTrue("Must include cash instruction line 2", text.contains("A REALIZAR EL PAGO"))
        assertTrue("Must include cash instruction line 3", text.contains("EN EFECTIVO"))
    }
}
