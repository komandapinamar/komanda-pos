package com.komanda.business.hardware.printing

import com.komanda.business.core.model.TicketPayload
import com.komanda.business.hardware.printing.enums.PrinterType
import com.komanda.business.hardware.printing.model.PrintResult

interface PrinterDriver {
    val type: PrinterType
    val name: String
    suspend fun isAvailable(): Boolean
    suspend fun printTicket(payload: TicketPayload): PrintResult
    suspend fun printRaw(bytes: ByteArray): PrintResult
}
