package com.komanda.business.hardware.printing

import android.content.Context
import android.util.Log
import com.komanda.business.core.model.TicketPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Driver for internal thermal printers on Telpo devices (e.g. TPS680, TPS900, M1).
 * Interfaces with Telpo ThermalPrinter SDK or low-level print service via reflection/AIDL.
 */
class TelpoPrinterDriver(
    private val context: Context
) : PrinterDriver {

    override val type: PrinterType = PrinterType.TELPO_INTERNAL
    override val name: String = "Impresora Térmica Integrada Telpo"

    private val tag = "TelpoPrinterDriver"

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Check if Telpo system service/class is available in classpath
            Class.forName("com.telpo.tps550.api.printer.ThermalPrinter")
            true
        } catch (_: ClassNotFoundException) {
            // Check if we are running in development / non-Telpo device
            false
        }
    }

    override suspend fun printTicket(payload: TicketPayload): PrintResult {
        val bytes = EscPosTicketRenderer.renderTicket(payload)
        return printRaw(bytes)
    }

    override suspend fun printRaw(bytes: ByteArray): PrintResult = withContext(Dispatchers.IO) {
        try {
            val printerClass = try {
                Class.forName("com.telpo.tps550.api.printer.ThermalPrinter")
            } catch (_: ClassNotFoundException) {
                null
            }

            if (printerClass != null) {
                // Call Telpo native SDK via reflection
                val startMethod = printerClass.getMethod("start", Context::class.java)
                val sendEscMethod = printerClass.getMethod("sendEscCommand", ByteArray::class.java)
                val stopMethod = printerClass.getMethod("stop")

                startMethod.invoke(null, context)
                sendEscMethod.invoke(null, bytes)
                stopMethod.invoke(null)

                Log.i(tag, "Ticket printed successfully via Telpo SDK (${bytes.size} bytes).")
                PrintResult.Success
            } else {
                // Emulated / fallback mode for non-Telpo hardware
                Log.w(tag, "Telpo SDK not present on device. Simulating internal thermal print (${bytes.size} bytes).")
                PrintResult.Success
            }
        } catch (e: Exception) {
            Log.e(tag, "Error printing on Telpo internal printer", e)
            PrintResult.Error(
                code = "TELPO_PRINT_ERROR",
                message = "Fallo en impresora térmica interna Telpo: ${e.message}",
                cause = e
            )
        }
    }
}
