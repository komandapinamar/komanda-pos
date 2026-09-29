package com.komanda.business.hardware.printing

import com.komanda.business.core.model.TicketCustomer
import com.komanda.business.core.model.TicketItem
import com.komanda.business.core.model.TicketItemOption
import com.komanda.business.core.model.TicketPayload
import com.komanda.business.core.model.TicketSummary
import com.komanda.business.core.model.FiscalInvoiceData
import com.komanda.business.core.model.orderTrackingUrl
import com.komanda.business.hardware.printing.renderer.EscPosTicketRenderer
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
    fun `renderCounterTicket outputs commercial receipt with prices totals and thank you text`() {
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

        // Branding
        assertTrue("Must include thank you message", text.contains("¡Gracias por tu compra!"))

        // Standardized pickup code
        assertTrue("Must include standardized pickup code block", text.contains("CODIGO DE RETIRO"))
        assertTrue("Must include purchase number fallback when PIN is null", text.contains("#42"))

        // With explicit PIN
        val withPin = samplePayload.copy(pickupPin = "7428")
        val pinBytes = EscPosTicketRenderer.renderCounterTicket(withPin)
        val pinText = String(pinBytes, Charsets.ISO_8859_1)
        assertTrue("Must include explicit PIN in pickup block", pinText.contains("#7428"))
    }

    @Test
    fun `Refill counter ticket prints bolt logo and encoded status URL only for counter`() {
        val url = "https://komanda.example/orders/status/1c5fb634-425c-4a7e-830e-1392d2e3d0ae/62d47dd7-d84d-4313-87f1-d0e72dfafafe"
        val refill = samplePayload.copy(tenant = " rEfIlL ", trackingUrl = url)
        val counter = String(EscPosTicketRenderer.renderCounterTicket(refill), Charsets.ISO_8859_1)
        assertTrue(counter.contains("| |   __/  _/"))
        assertTrue(counter.contains("Segui tu pedido:"))
        assertTrue(counter.contains(url)) // QR store-data command carries the UTF-8 URL.
        assertTrue(counter.contains("¡Gracias por tu compra!"))
        assertTrue(counter.lines().filter { it.contains("| |") }.all { it.length <= 32 })

        val kitchen = String(EscPosTicketRenderer.renderKitchenTicket(refill), Charsets.ISO_8859_1)
        assertFalse(kitchen.contains(url))
        assertFalse(kitchen.contains("| |   __/  _/"))

        val other = String(EscPosTicketRenderer.renderCounterTicket(refill.copy(tenant = "Otro")), Charsets.ISO_8859_1)
        assertFalse(other.contains("| |   __/  _/"))
        val test = String(EscPosTicketRenderer.renderCounterTicket(samplePayload), Charsets.ISO_8859_1)
        assertFalse(test.contains("Segui tu pedido:"))
    }

    @Test
    fun `tracking URL requires real UUIDs and HTTPS server`() {
        val tenant = "1c5fb634-425c-4a7e-830e-1392d2e3d0ae"
        val order = "62d47dd7-d84d-4313-87f1-d0e72dfafafe"
        assertEquals("https://komanda.example/orders/status/$tenant/$order", orderTrackingUrl("https://komanda.example/", tenant, order))
        assertEquals(null, orderTrackingUrl("http://komanda.example", tenant, order))
        assertEquals(null, orderTrackingUrl("https://komanda.example/private", tenant, order))
        assertEquals(null, orderTrackingUrl("https://komanda.example", tenant, "test-check"))
    }

    @Test
    fun `counter ticket keeps fiscal QR alongside tracking QR`() {
        val tracking = "https://komanda.example/orders/status/1c5fb634-425c-4a7e-830e-1392d2e3d0ae/62d47dd7-d84d-4313-87f1-d0e72dfafafe"
        val fiscal = "https://www.afip.gob.ar/fe/qr/?p=test"
        val payload = samplePayload.copy(
            trackingUrl = tracking,
            fiscalInfo = FiscalInvoiceData("B", 1, 7L, "1234", "2026-09-23", fiscal)
        )
        val text = String(EscPosTicketRenderer.renderCounterTicket(payload), Charsets.ISO_8859_1)
        assertTrue(text.contains(fiscal))
        assertTrue(text.contains(tracking))
    }
}
