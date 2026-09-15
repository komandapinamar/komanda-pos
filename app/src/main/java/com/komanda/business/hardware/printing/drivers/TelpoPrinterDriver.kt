package com.komanda.business.hardware.printing.drivers

import android.content.Context
import com.komanda.business.core.model.TicketPayload
import com.komanda.business.hardware.printing.enums.PrinterType
import com.komanda.business.hardware.printing.model.PrintResult
import com.komanda.business.hardware.printing.PrinterDriver

/**
 * Adapter for the internal thermal printer on Telpo POS terminals (TPS680, etc.).
 * Internally delegates to [UsbEscPosDriver] using DantSu ESC/POS library.
 */
class TelpoPrinterDriver(
    context: Context
) : PrinterDriver {

    private val delegate = UsbEscPosDriver(context)

    override val type: PrinterType = PrinterType.TELPO_INTERNAL
    override val name: String = "Impresora Térmica Integrada Telpo"

    override suspend fun isAvailable(): Boolean = delegate.isAvailable()

    override suspend fun printTicket(payload: TicketPayload): PrintResult = delegate.printTicket(payload)

    override suspend fun printRaw(bytes: ByteArray): PrintResult = delegate.printRaw(bytes)
}
