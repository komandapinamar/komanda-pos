package com.komanda.business.hardware.printing

import com.komanda.business.core.model.TicketPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class NetworkEscPosDriver(
    private val host: String,
    private val port: Int = 9100,
    private val timeoutMs: Int = 4000
) : PrinterDriver {

    override val type: PrinterType = PrinterType.NETWORK_ESC_POS
    override val name: String = "Impresora de Red ($host:$port)"

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun printTicket(payload: TicketPayload): PrintResult {
        val bytes = EscPosTicketRenderer.renderTicket(payload)
        return printRaw(bytes)
    }

    override suspend fun printRaw(bytes: ByteArray): PrintResult = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                socket.getOutputStream().apply {
                    write(bytes)
                    flush()
                }
            }
            PrintResult.Success
        } catch (e: Exception) {
            PrintResult.Error(
                code = "NETWORK_PRINT_FAILED",
                message = "Fallo al enviar datos a $host:$port: ${e.message}",
                cause = e
            )
        }
    }
}
