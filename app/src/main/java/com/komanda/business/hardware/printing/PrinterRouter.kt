package com.komanda.business.hardware.printing

import android.content.Context
import android.util.Log
import com.komanda.business.core.model.TicketCustomer
import com.komanda.business.core.model.TicketItem
import com.komanda.business.core.model.TicketPayload
import com.komanda.business.core.model.TicketSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.util.Collections

/**
 * Orchestrates multi-printer dispatching according to configured roles, event triggers,
 * and the master automatic printing switch.
 */
class PrinterRouter(
    private val context: Context,
    val repository: PrinterConfigRepository = PrinterConfigRepository(context)
) {
    private val tag = "PrinterRouter"

    val printers: StateFlow<List<PrinterConfig>> = repository.printers
    val masterAutoPrintEnabled: StateFlow<Boolean> = repository.masterAutoPrintEnabled

    // Memory cache of printed order IDs to prevent any duplicate/looping auto-prints
    private val alreadyPrintedOrderIds: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    fun setMasterAutoPrint(enabled: Boolean) {
        repository.setMasterAutoPrint(enabled)
    }

    /**
     * Resolves an executable [PrinterDriver] from a given configuration profile.
     */
    fun getDriverForConfig(config: PrinterConfig): PrinterDriver {
        return when (config.type) {
            PrinterType.USB_ESC_POS -> UsbEscPosDriver(context)
            PrinterType.NETWORK_ESC_POS -> {
                val addr = config.address ?: "192.168.1.100:9100"
                val parts = addr.split(":")
                val host = parts[0].trim()
                val port = parts.getOrNull(1)?.toIntOrNull() ?: 9100
                NetworkEscPosDriver(host = host, port = port)
            }
            PrinterType.BLUETOOTH_ESC_POS -> {
                BluetoothEscPosDriver(
                    context = context,
                    macAddress = config.address ?: "",
                    deviceName = config.name
                )
            }
            PrinterType.TELPO_INTERNAL -> TelpoPrinterDriver(context)
        }
    }

    /**
     * Dispatches automatic printing for newly arrived orders (online / SSE / background sync).
     */
    suspend fun handleNewOrder(payload: TicketPayload) = withContext(Dispatchers.IO) {
        if (!masterAutoPrintEnabled.value) {
            Log.d(tag, "Master auto-print is OFF. Skipping new order print for ${payload.orderId}.")
            return@withContext
        }

        if (payload.orderId in alreadyPrintedOrderIds) {
            Log.d(tag, "Order ${payload.orderId} was already printed. Skipping.")
            return@withContext
        }
        alreadyPrintedOrderIds.add(payload.orderId)

        val targets = printers.value.filter {
            it.role != PrinterRole.DISABLED &&
                    (it.trigger == PrintTrigger.ON_NEW_ORDER || it.trigger == PrintTrigger.ALWAYS_AUTOMATIC)
        }

        dispatchToPrinters(payload, targets, eventDesc = "NEW_ORDER")
    }

    /**
     * Dispatches automatic printing for direct counter orders entered on the POS.
     */
    suspend fun handleDirectPosOrder(payload: TicketPayload) = withContext(Dispatchers.IO) {
        if (!masterAutoPrintEnabled.value) {
            Log.d(tag, "Master auto-print is OFF. Skipping direct order print for ${payload.orderId}.")
            return@withContext
        }

        if (payload.orderId in alreadyPrintedOrderIds) {
            Log.d(tag, "Direct order ${payload.orderId} was already printed. Skipping.")
            return@withContext
        }
        alreadyPrintedOrderIds.add(payload.orderId)

        val targets = printers.value.filter {
            it.role != PrinterRole.DISABLED &&
                    (it.trigger == PrintTrigger.ON_DIRECT_POS_ORDER || it.trigger == PrintTrigger.ALWAYS_AUTOMATIC)
        }

        dispatchToPrinters(payload, targets, eventDesc = "DIRECT_POS_ORDER")
    }

    /**
     * Manually prints a ticket on a specific target printer (or all active printers if "ALL" or null).
     */
    suspend fun printManual(
        payload: TicketPayload,
        targetPrinterId: String? = null
    ): Map<String, PrintResult> = withContext(Dispatchers.IO) {
        val targets = if (targetPrinterId == null || targetPrinterId == "ALL") {
            printers.value.filter { it.role != PrinterRole.DISABLED }
        } else {
            printers.value.filter { it.id == targetPrinterId && it.role != PrinterRole.DISABLED }
        }

        val results = mutableMapOf<String, PrintResult>()
        for (config in targets) {
            val driver = getDriverForConfig(config)
            val bytes = EscPosTicketRenderer.renderTicket(payload.copy(copies = config.copies), config.role)
            val res = driver.printRaw(bytes)
            results[config.id] = res
            Log.i(tag, "Manual print on '${config.name}' (${config.role}): $res")
        }
        results
    }

    /**
     * Prints an Espresso self-service cash ticket with prominent payment notice.
     */
    suspend fun printEspressoCashTicket(payload: TicketPayload): Map<String, PrintResult> = withContext(Dispatchers.IO) {
        val targets = printers.value.filter { it.role != PrinterRole.DISABLED }
        val results = mutableMapOf<String, PrintResult>()
        val bytes = EscPosTicketRenderer.renderEspressoCashTicket(payload)
        for (config in targets) {
            val driver = getDriverForConfig(config)
            val res = driver.printRaw(bytes)
            results[config.id] = res
            Log.i(tag, "Espresso cash ticket printed on '${config.name}': $res")
        }
        results
    }

    /**
     * Prints a diagnostic test ticket on a given printer profile.
     */
    suspend fun printTestTicket(config: PrinterConfig): PrintResult = withContext(Dispatchers.IO) {
        val testPayload = TicketPayload(
            orderId = "test-check",
            purchaseNumber = "TEST",
            source = "pos_test",
            copies = 1,
            tenant = "Komanda Demo",
            customer = TicketCustomer(name = "Prueba de Impresión"),
            items = listOf(
                TicketItem(
                    id = "test-1",
                    name = "Ticket de Verificación",
                    quantity = 1,
                    unitPrice = 100.0,
                    lineTotal = 100.0
                )
            ),
            summary = TicketSummary(subtotal = 100.0, total = 100.0)
        )

        val driver = getDriverForConfig(config)
        val bytes = EscPosTicketRenderer.renderTicket(testPayload, config.role)
        driver.printRaw(bytes)
    }

    private suspend fun dispatchToPrinters(
        payload: TicketPayload,
        targets: List<PrinterConfig>,
        eventDesc: String
    ) {
        if (targets.isEmpty()) {
            Log.d(tag, "No printers configured for event $eventDesc.")
            return
        }

        for (config in targets) {
            try {
                val driver = getDriverForConfig(config)
                val bytes = EscPosTicketRenderer.renderTicket(payload.copy(copies = config.copies), config.role)
                val result = driver.printRaw(bytes)
                Log.i(tag, "Auto-printed [$eventDesc] on '${config.name}' (${config.role}): $result")
            } catch (e: Exception) {
                Log.e(tag, "Failed auto-printing on '${config.name}'", e)
            }
        }
    }
}
