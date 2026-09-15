package com.komanda.business.hardware.printing.drivers

import com.dantsu.escposprinter.connection.tcp.TcpConnection
import com.komanda.business.core.model.TicketPayload
import com.komanda.business.hardware.printing.renderer.EscPosTicketRenderer
import com.komanda.business.hardware.printing.enums.PrinterType
import com.komanda.business.hardware.printing.model.PrintResult
import com.komanda.business.hardware.printing.PrinterDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Network ESC/POS printer driver powered by DantSu ESCPOS-ThermalPrinter-Android (TcpConnection).
 */
class NetworkEscPosDriver(
    private val host: String,
    private val port: Int = 9100,
    private val timeoutMs: Int = 4000
) : PrinterDriver {

    override val type: PrinterType = PrinterType.NETWORK_ESC_POS
    override val name: String = "Impresora de Red ($host:$port)"

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        val connection = TcpConnection(host, port, timeoutMs)
        try {
            connection.connect()
            connection.disconnect()
            true
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun printTicket(payload: TicketPayload): PrintResult {
        val bytes = EscPosTicketRenderer.renderTicket(payload)
        return printRaw(bytes)
    }

    override suspend fun printRaw(bytes: ByteArray): PrintResult = withContext(Dispatchers.IO) {
        val connection = TcpConnection(host, port, timeoutMs)
        try {
            connection.connect()
            connection.write(bytes)
            connection.send()
            PrintResult.Success
        } catch (e: Exception) {
            PrintResult.Error(
                code = "NETWORK_PRINT_FAILED",
                message = "Fallo al enviar datos a $host:$port: ${e.message}",
                cause = e
            )
        } finally {
            try {
                connection.disconnect()
            } catch (_: Exception) {}
        }
    }
}
