package com.komanda.business.hardware.printing.renderer

import android.graphics.Bitmap
import com.komanda.business.core.model.FiscalInvoiceData
import com.komanda.business.core.model.TicketPayload
import com.komanda.business.hardware.printing.enums.PrinterRole
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Generates raw ESC/POS byte streams for thermal printers (32 columns, 58mm/80mm).
 * Produces differentiated ticket layouts for Kitchen (operational) and Counter (commercial).
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

    /**
     * Renders a ticket tailored to the specified printer role.
     * Optionally accepts a [logoBitmap] to print a custom raster logo at the bottom.
     */
    fun renderTicket(
        payload: TicketPayload,
        role: PrinterRole = PrinterRole.COUNTER,
        logoBitmap: Bitmap? = null,
        headerLogoBitmap: Bitmap? = null
    ): ByteArray {
        val stream = ByteArrayOutputStream()
        val copies = if (payload.copies > 0) payload.copies else 1

        for (copyIndex in 0 until copies) {
            when (role) {
                PrinterRole.KITCHEN -> renderKitchenCopy(stream, payload, headerLogoBitmap)
                PrinterRole.COUNTER, PrinterRole.DISABLED -> renderCounterCopy(
                    stream,
                    payload,
                    copyIndex,
                    copies,
                    logoBitmap,
                    headerLogoBitmap
                )
            }
        }

        return stream.toByteArray()
    }

    /**
     * Kitchen Ticket Template:
     * Focuses 100% on preparation. Strictly omits prices, totals, and database internal UUIDs.
     */
    fun renderKitchenTicket(
        payload: TicketPayload,
        headerLogoBitmap: Bitmap? = null
    ): ByteArray {
        val stream = ByteArrayOutputStream()
        renderKitchenCopy(stream, payload, headerLogoBitmap)
        return stream.toByteArray()
    }

    /**
     * Counter / Customer Ticket Template:
     * Full commercial receipt with itemized prices, totals, payment status, optional fiscal block,
     * and tenant-specific branding with thank-you text.
     */
    fun renderCounterTicket(
        payload: TicketPayload,
        logoBitmap: Bitmap? = null,
        headerLogoBitmap: Bitmap? = null
    ): ByteArray {
        val stream = ByteArrayOutputStream()
        val copies = if (payload.copies > 0) payload.copies else 1
        for (i in 0 until copies) {
            renderCounterCopy(stream, payload, i, copies, logoBitmap, headerLogoBitmap)
        }
        return stream.toByteArray()
    }

    private fun renderSharedHeader(
        stream: ByteArrayOutputStream,
        payload: TicketPayload,
        subtitle: String? = null,
        headerLogoBitmap: Bitmap? = null
    ) {
        stream.write(CMD_INIT)
        stream.write(CMD_ALIGN_CENTER)

        // 1. Primary Title: KOMANDA
        stream.write(CMD_SIZE_DOUBLE)
        stream.write(CMD_BOLD_ON)
        writeText(stream, "KOMANDA\n")

        // 2. Subtitle: Restaurant Name or Header Logo
        stream.write(CMD_SIZE_NORMAL)
        stream.write(CMD_BOLD_ON)
        if (headerLogoBitmap != null) {
            writeBitmap(stream, headerLogoBitmap)
        } else {
            writeText(stream, "${payload.tenant.uppercase()}\n")
        }

        subtitle?.let {
            writeText(stream, "$it\n")
        }

        val purchaseNumber = payload.purchaseNumber
        if (!purchaseNumber.isNullOrBlank()) {
            writeText(stream, "Compra #$purchaseNumber\n")
        } else {
            writeText(stream, "Orden #${payload.orderId}\n")
        }

        payload.pickupPin?.takeIf { it.isNotBlank() }?.let { pin ->
            writeText(stream, "PIN Retiro: #$pin\n")
        }

        stream.write(CMD_BOLD_OFF)
        writeText(stream, "${formatTimestamp(payload.approvedAt)}\n")
        writeRule(stream)
    }

    private fun renderKitchenCopy(
        stream: ByteArrayOutputStream,
        payload: TicketPayload,
        headerLogoBitmap: Bitmap? = null
    ) {
        renderSharedHeader(stream, payload, subtitle = "--- COCINA ---", headerLogoBitmap = headerLogoBitmap)

        // Customer Info
        stream.write(CMD_ALIGN_LEFT)
        stream.write(CMD_BOLD_ON)
        writeText(stream, "CLIENTE\n")
        stream.write(CMD_BOLD_OFF)
        writeWrapped(stream, payload.customer.name?.ifBlank { "Sin nombre" } ?: "Sin nombre")

        payload.customer.phone?.takeIf { it.isNotBlank() }?.let { phone ->
            writeWrapped(stream, "Telefono: $phone")
        }

        payload.customer.address?.takeIf { it.isNotBlank() }?.let { address ->
            writeWrapped(stream, "Direccion: $address")
        }

        // Notes / Observations (emphasized for kitchen staff)
        if (!payload.notes.isNullOrBlank()) {
            writeRule(stream)
            stream.write(CMD_BOLD_ON)
            writeText(stream, "OBSERVACIONES\n")
            stream.write(CMD_BOLD_OFF)
            writeWrapped(stream, payload.notes.trim())
        }

        // Order Lines (Items and options, NO PRICES)
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
        }

        // Summary: Physical units only
        writeRule(stream)
        writeText(stream, "Total unidades: $totalUnits\n")

        // Feed & Cut
        writeText(stream, "\n\n\n")
        stream.write(CMD_FEED_AND_CUT)
    }

    private fun renderCounterCopy(
        stream: ByteArrayOutputStream,
        payload: TicketPayload,
        copyIndex: Int,
        totalCopies: Int,
        logoBitmap: Bitmap? = null,
        headerLogoBitmap: Bitmap? = null
    ) {
        val copyLabel = if (totalCopies > 1) "COPIA ${copyIndex + 1}/$totalCopies" else null
        renderSharedHeader(stream, payload, subtitle = copyLabel, headerLogoBitmap = headerLogoBitmap)

        // Customer Info
        stream.write(CMD_ALIGN_LEFT)
        stream.write(CMD_BOLD_ON)
        writeText(stream, "CLIENTE\n")
        stream.write(CMD_BOLD_OFF)
        writeWrapped(stream, payload.customer.name?.ifBlank { "Sin nombre" } ?: "Sin nombre")

        payload.customer.phone?.takeIf { it.isNotBlank() }?.let { phone ->
            writeWrapped(stream, "Telefono: $phone")
        }

        payload.customer.address?.takeIf { it.isNotBlank() }?.let { address ->
            writeWrapped(stream, "Direccion: $address")
        }

        // Notes / Observations
        if (!payload.notes.isNullOrBlank()) {
            writeRule(stream)
            stream.write(CMD_BOLD_ON)
            writeText(stream, "OBSERVACIONES\n")
            stream.write(CMD_BOLD_OFF)
            writeWrapped(stream, payload.notes.trim())
        }

        // Order Lines with Prices
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
                val deltaStr = if (option.priceDelta > 0) " (+${formatMoney(option.priceDelta, payload.currency)})" else ""
                writeWrapped(stream, "+ ${option.name}$deltaStr", indent = "  ")
            }
            writeText(stream, "    ${formatMoney(item.lineTotal, payload.currency)}\n")
        }

        // Financial Summary
        writeRule(stream)
        writeText(stream, "Unidades: $totalUnits\n")

        if (payload.summary.discountTotal > 0) {
            writeText(stream, "Subtotal: ${formatMoney(payload.summary.subtotal, payload.currency)}\n")
            writeText(stream, "Descuento: -${formatMoney(payload.summary.discountTotal, payload.currency)}\n")
        }

        stream.write(CMD_BOLD_ON)
        writeText(stream, "Total: ${formatMoney(payload.summary.total, payload.currency)}\n")
        stream.write(CMD_BOLD_OFF)
        writeText(stream, "Estado: ${getStatusLabel(payload.source)}\n")
        writeRule(stream)

        // AFIP Fiscal QR / Data if present
        payload.fiscalInfo?.let { fiscal ->
            renderFiscalBlock(stream, fiscal)
        }

        // Standardized Pickup Code Block for Counter
        val codeToDisplay = payload.pickupPin?.takeIf { it.isNotBlank() } ?: payload.purchaseNumber?.takeIf { it.isNotBlank() }
        if (!codeToDisplay.isNullOrBlank()) {
            writeRule(stream)
            stream.write(CMD_ALIGN_CENTER)
            stream.write(CMD_BOLD_ON)
            writeText(stream, "CODIGO DE RETIRO\n")
            stream.write(CMD_SIZE_DOUBLE)
            writeText(stream, "#$codeToDisplay\n")
            stream.write(CMD_SIZE_NORMAL)
            writeText(stream, "Presenta este comprobante para retirar\n")
            stream.write(CMD_BOLD_OFF)
            writeRule(stream)
        }

        payload.trackingUrl?.takeIf { it.startsWith("https://") }?.let { url ->
            stream.write(CMD_ALIGN_CENTER)
            writeText(stream, "Segui tu pedido:\n")
            writeQrCode(stream, url)
        }

        renderCounterFooter(stream)

        // Feed & Cut
        writeText(stream, "\n\n\n")
        stream.write(CMD_FEED_AND_CUT)
    }

    private fun renderCounterFooter(stream: ByteArrayOutputStream) {
        stream.write(CMD_ALIGN_CENTER)
        stream.write(CMD_BOLD_ON)
        writeText(stream, "¡Gracias por tu compra!\n")
        stream.write(CMD_BOLD_OFF)
    }

    /**
     * Converts an Android [Bitmap] to ESC/POS raster bit image command (GS v 0) and writes it to [stream].
     *
     * @param stream Target output byte stream
     * @param bitmap Input bitmap image
     * @param maxWidthDots Maximum width in printer dots/pixels (default 384 for 58mm printer at 203 DPI)
     */
    fun writeBitmap(
        stream: ByteArrayOutputStream,
        bitmap: Bitmap,
        maxWidthDots: Int = 384
    ) {
        val scaledBitmap = if (bitmap.width > maxWidthDots) {
            val aspectRatio = bitmap.height.toFloat() / bitmap.width.toFloat()
            val targetHeight = (maxWidthDots * aspectRatio).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, maxWidthDots, targetHeight, true)
        } else {
            bitmap
        }

        val width = scaledBitmap.width
        val height = scaledBitmap.height
        val widthBytes = (width + 7) / 8

        stream.write(CMD_ALIGN_CENTER)
        // GS v 0 0 xL xH yL yH
        stream.write(
            byteArrayOf(
                0x1D, 0x76, 0x30, 0x00,
                (widthBytes and 0xFF).toByte(),
                ((widthBytes shr 8) and 0xFF).toByte(),
                (height and 0xFF).toByte(),
                ((height shr 8) and 0xFF).toByte()
            )
        )

        val imageBytes = ByteArray(widthBytes * height)
        var byteIdx = 0

        for (y in 0 until height) {
            for (xByte in 0 until widthBytes) {
                var accumulator = 0
                for (bit in 0 until 8) {
                    val x = xByte * 8 + bit
                    if (x < width) {
                        val pixel = scaledBitmap.getPixel(x, y)
                        val alpha = (pixel shr 24) and 0xFF
                        val red = (pixel shr 16) and 0xFF
                        val green = (pixel shr 8) and 0xFF
                        val blue = pixel and 0xFF

                        // Print black dot if pixel is visible and dark enough (luminance < 128)
                        if (alpha > 128) {
                            val luminance = (0.299 * red + 0.587 * green + 0.114 * blue).toInt()
                            if (luminance < 128) {
                                accumulator = accumulator or (0x80 shr bit)
                            }
                        }
                    }
                }
                imageBytes[byteIdx++] = accumulator.toByte()
            }
        }

        stream.write(imageBytes)
        stream.write(byteArrayOf(0x0A))
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
        writeRule(stream)
    }

    private fun writeQrCode(stream: ByteArrayOutputStream, qrData: String) {
        val qrBytes = qrData.toByteArray(Charsets.UTF_8)
        val length = qrBytes.size + 3
        val pL = (length and 0xFF).toByte()
        val pH = ((length shr 8) and 0xFF).toByte()

        // 1. Model type (Model 2)
        stream.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00))
        // 2. Module size (6 dots for prominent, easy-to-scan QR)
        stream.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, 0x06))
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
