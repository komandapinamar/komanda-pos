package com.komanda.business.hardware.printing

import com.komanda.business.core.model.TicketPayload

sealed class PrintResult {
    data object Success : PrintResult()
    data class Error(val code: String, val message: String, val cause: Throwable? = null) : PrintResult()
}

enum class PrinterType {
    TELPO_INTERNAL,
    USB_ESC_POS,
    NETWORK_ESC_POS,
    BLUETOOTH_ESC_POS
}

interface PrinterDriver {
    val type: PrinterType
    val name: String
    suspend fun isAvailable(): Boolean
    suspend fun printTicket(payload: TicketPayload): PrintResult
    suspend fun printRaw(bytes: ByteArray): PrintResult
}
