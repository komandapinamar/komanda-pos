package com.komanda.business.hardware.printing

import com.komanda.business.core.model.FiscalInvoiceData
import com.komanda.business.core.model.TicketPayload
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Port of komanda/print-service/komanda_print/renderer.py to Kotlin.
 * Generates raw ESC/POS byte streams for thermal printers (32 columns, 58mm/80mm).
 */
object EscPosTicketRenderer {

    const val PRINTER_LINE_WIDTH = 32
    private val CHARSET_LATIN: Charset = Charset.forName("ISO-8859-1")

    // ESC/POS Command Constants
    private val CMD_INIT = byteArrayOf(0x1B, 0x40) // ESC @
    private val CMD_ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00) // ESC a 0
    private val CMD_ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01) // ESC a 1
    private val CMD_ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02) // ESC a 2
    private val CMD_BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01) // ESC E 1
    private val CMD_BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00) // ESC E 0
    private val CMD_SIZE_NORMAL = byteArrayOf(0x1D, 0x21, 0x00) // GS ! 0
    private val CMD_SIZE_DOUBLE = byteArrayOf(0x1D, 0x21, 0x11) // GS ! 0x11 (double width & height)
    private val CMD_FEED_AND_CUT = byteArrayOf(0x1D, 0x56, 0x42, 0x02) // GS V 'B' 2

    fun renderTicket(payload: TicketPayload): ByteArray {
        val stream = ByteArrayOutputStream()
        val copies = if (payload.copies > 0) payload.copies else 1

        for (copyIndex in 0 until copies) {
            renderSingleTicketCopy(stream, payload, copyIndex, copies)
        }

        return stream.toByteArray()
    }

    private fun renderSingleTicketCopy(
        stream: ByteArrayOutputStream,
        payload: TicketPayload,
        copyIndex: Int,
        totalCopies: Int
    ) {
        // Initialize printer
        stream.write(CMD_INIT)

        // Header
        stream.write(CMD_ALIGN_CENTER)
        stream.write(CMD_SIZE_DOUBLE)
        stream.write(CMD_BOLD_ON)
        writeText(stream, "${payload.tenant.uppercase()}\n")

        stream.write(CMD_SIZE_NORMAL)
        stream.write(CMD_BOLD_ON)
        writeText(stream, "${getCopyLabel(payload.source, copyIndex, totalCopies)}\n")

        stream.write(CMD_BOLD_OFF)
        writeText(stream, "${getStatusLabel(payload.source)}\n")

        val purchaseNumber = payload.purchaseNumber
        if (!purchaseNumber.isNullOrBlank()) {
            writeText(stream, "Compra #$purchaseNumber\n")
        } else {
            writeText(stream, "Orden #${payload.orderId}\n")
        }

        writeText(stream, "${formatTimestamp(payload.approvedAt)}\n")
        writeRule(stream)

        // Customer Info
        stream.write(CMD_ALIGN_LEFT)
        stream.write(CMD_BOLD_ON)
        writeText(stream, "CLIENTE\n")
        stream.write(CMD_BOLD_OFF)
        writeWrapped(stream, payload.customer.name?.ifBlank { "Sin nombre" } ?: "Sin nombre")

        payload.customer.phone?.let { phone ->
            if (phone.isNotBlank()) {
                writeWrapped(stream, "Telefono: $phone")
            }
        }

        payload.customer.address?.let { address ->
            if (address.isNotBlank()) {
                writeWrapped(stream, "Direccion: $address")
            }
        }

        // Notes / Observations
        if (!payload.notes.isNullOrBlank()) {
            writeRule(stream)
            stream.write(CMD_BOLD_ON)
            writeText(stream, "OBSERVACIONES\n")
            stream.write(CMD_BOLD_OFF)
            writeWrapped(stream, payload.notes.trim())
        }

        // Order Lines
        writeRule(stream)
        stream.write(CMD_BOLD_ON)
        writeText(stream, "PEDIDO\n")
        stream.write(CMD_BOLD_OFF)

        var totalUnits = 0
        for (item in payload.items) {
            totalUnits += item.quantity
            stream.write(CMD_BOLD_ON)
            writeWrapped(stream, "${item.quantity} x ${item.name}")
            stream.write(CMD_BOLD_OFF)

            for (option in item.options) {
                writeWrapped(stream, "+ ${option.name}", indent = "  ")
            }
            writeText(stream, "    ${formatMoney(item.lineTotal, payload.currency)}\n")
        }

        // Summary
        writeRule(stream)
        writeText(stream, "Lineas: ${payload.items.size}\n")
        writeText(stream, "Unidades: $totalUnits\n")

        if (payload.summary.discountTotal > 0) {
            writeText(stream, "Subtotal: ${formatMoney(payload.summary.subtotal, payload.currency)}\n")
            writeText(stream, "Descuento: ${formatMoney(payload.summary.discountTotal, payload.currency)}\n")
        }

        stream.write(CMD_BOLD_ON)
        writeText(stream, "Total: ${formatMoney(payload.summary.total, payload.currency)}\n")
        stream.write(CMD_BOLD_OFF)
        writeRule(stream)

        // Internal order ID
        writeText(stream, "Orden interna: ${payload.orderId}\n")

        // AFIP Fiscal QR / Data if present
        payload.fiscalInfo?.let { fiscal ->
            renderFiscalBlock(stream, fiscal)
        }

        // Feed & Cut
        writeText(stream, "\n\n\n")
        stream.write(CMD_FEED_AND_CUT)
    }

    private fun renderFiscalBlock(stream: ByteArrayOutputStream, fiscal: FiscalInvoiceData) {
        writeRule(stream)
        stream.write(CMD_ALIGN_CENTER)
        stream.write(CMD_BOLD_ON)
        writeText(stream, "COMPROBANTE FACTURA ${fiscal.invoiceType}\n")
        stream.write(CMD_BOLD_OFF)
        writeText(stream, "P.V. ${fiscal.pointOfSale.toString().padStart(4, '0')} - Nro ${fiscal.invoiceNumber.toString().padStart(8, '0')}\n")
        writeText(stream, "CAE: ${fiscal.cae}\n")
        writeText(stream, "Vto. CAE: ${fiscal.caeDueDate}\n")

        fiscal.qrData?.let { qr ->
            writeQrCode(stream, qr)
        }
    }

    private fun writeQrCode(stream: ByteArrayOutputStream, qrData: String) {
        val qrBytes = qrData.toByteArray(Charsets.UTF_8)
        val length = qrBytes.size + 3
        val pL = (length and 0xFF).toByte()
        val pH = ((length shr 8) and 0xFF).toByte()

        // 1. Model type (Model 2)
        stream.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00))
        // 2. Module size (4 dots)
        stream.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, 0x04))
        // 3. Error correction level (Level L)
        stream.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x45, 0x30))
        // 4. Store data
        stream.write(byteArrayOf(0x1D, 0x28, 0x6B, pL, pH, 0x31, 0x50, 0x30))
        stream.write(qrBytes)
        // 5. Print QR symbol
        stream.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30))
        stream.write(byteArrayOf(0x0A))
    }

    fun formatMoney(amount: Double, currency: String = "ARS"): String {
        val symbols = DecimalFormatSymbols(Locale("es", "AR")).apply {
            decimalSeparator = ','
            groupingSeparator = '.'
        }
        val format = DecimalFormat("#,##0.00", symbols)
        var formatted = format.format(amount)
        if (formatted.endsWith(",00")) {
            formatted = formatted.substring(0, formatted.length - 3)
        }
        return if (currency.equals("ARS", ignoreCase = true)) {
            "$$formatted"
        } else {
            "${currency.uppercase()} $formatted"
        }
    }

    fun formatTimestamp(isoDate: String?): String {
        val outFormat = SimpleDateFormat("dd/MM HH:mm", Locale("es", "AR")).apply {
            timeZone = TimeZone.getTimeZone("America/Argentina/Buenos_Aires")
        }
        if (isoDate.isNullOrBlank()) {
            return outFormat.format(Date())
        }
        return try {
            val inFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val cleaned = isoDate.split(".")[0].replace("Z", "")
            val parsed = inFormat.parse(cleaned) ?: Date()
            outFormat.format(parsed)
        } catch (_: Exception) {
            outFormat.format(Date())
        }
    }

    fun getCopyLabel(source: String, copyIndex: Int, totalCopies: Int): String {
        if (source == "admin_direct" || source == "admin-direct") {
            return if (copyIndex == 0) "COCINA" else "CAJA / ENTREGA"
        }
        return if (totalCopies > 1) "COPIA ${copyIndex + 1}/$totalCopies" else "COCINA"
    }

    fun getStatusLabel(source: String): String {
        return if (source == "admin_direct" || source == "admin-direct") {
            "COBRAR EN CAJA"
        } else {
            "PAGADO"
        }
    }

    private fun writeRule(stream: ByteArrayOutputStream, char: Char = '-') {
        val rule = char.toString().repeat(PRINTER_LINE_WIDTH) + "\n"
        writeText(stream, rule)
    }

    private fun writeWrapped(stream: ByteArrayOutputStream, text: String, indent: String = "") {
        val cleaned = text.split("\\s+".toRegex()).filter { it.isNotBlank() }.joinToString(" ")
        if (cleaned.isBlank()) return

        val maxWidth = (PRINTER_LINE_WIDTH - indent.length).coerceAtLeast(8)
        val words = cleaned.split(" ")
        var currentLine = StringBuilder()

        for (word in words) {
            if (currentLine.isEmpty()) {
                currentLine.append(word)
            } else if (currentLine.length + 1 + word.length <= maxWidth) {
                currentLine.append(" ").append(word)
            } else {
                writeText(stream, "$indent$currentLine\n")
                currentLine = StringBuilder(word)
            }
        }
        if (currentLine.isNotEmpty()) {
            writeText(stream, "$indent$currentLine\n")
        }
    }

    private fun writeText(stream: ByteArrayOutputStream, text: String) {
        val bytes = text.toByteArray(CHARSET_LATIN)
        stream.write(bytes)
    }
}
