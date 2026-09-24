package com.komanda.business.hardware.printing

import android.content.Context
import android.util.Log
import com.komanda.business.core.model.TicketCustomer
import com.komanda.business.core.model.TicketItem
import com.komanda.business.core.model.TicketPayload
import com.komanda.business.core.model.TicketSummary
import com.komanda.business.hardware.printing.enums.PrintTrigger
import com.komanda.business.hardware.printing.enums.PrinterRole
import com.komanda.business.hardware.printing.factory.PrinterDriverFactory
import com.komanda.business.hardware.printing.model.PrintResult
import com.komanda.business.hardware.printing.model.PrinterConfig
import com.komanda.business.hardware.printing.model.PrinterConfigRepository
import com.komanda.business.hardware.printing.renderer.EscPosTicketRenderer
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
    val repository: PrinterConfigRepository = PrinterConfigRepository(context),
    private val driverFactory: PrinterDriverFactory = PrinterDriverFactory(context),
    val journalStore: com.komanda.business.hardware.printing.model.PrintJournalStore =
        com.komanda.business.hardware.printing.model.PrintJournalStore(context)
) {
    private val tag = "PrinterRouter"

    val printers: StateFlow<List<PrinterConfig>> = repository.printers
    val masterAutoPrintEnabled: StateFlow<Boolean> = repository.masterAutoPrintEnabled

    fun setMasterAutoPrint(enabled: Boolean) {
        repository.setMasterAutoPrint(enabled)
    }

    /**
     * Resolves an executable [PrinterDriver] from a given configuration profile.
     */
    fun getDriverForConfig(config: PrinterConfig): PrinterDriver {
        return driverFactory.createDriver(config)
    }

    /**
     * Dispatches automatic printing for newly arrived orders (online / SSE / background sync).
     */
    suspend fun handleNewOrder(payload: TicketPayload) = withContext(Dispatchers.IO) {
        if (!masterAutoPrintEnabled.value) {
            Log.d(tag, "Master auto-print is OFF. Skipping new order print for ${payload.orderId}.")
            return@withContext
        }

        val targets = printers.value.filter {
            it.role != PrinterRole.DISABLED &&
                    (it.trigger == PrintTrigger.ON_NEW_ORDER || it.trigger == PrintTrigger.ALWAYS_AUTOMATIC)
        }

        val unprintedTargets = targets.filter {
            !journalStore.isDestinationPrinted(payload.orderId, "${it.id}_${it.role}")
        }

        if (unprintedTargets.isEmpty() && targets.isNotEmpty()) {
            Log.d(tag, "All targets for order ${payload.orderId} were already printed. Skipping.")
            return@withContext
        }

        dispatchToPrinters(payload, unprintedTargets, eventDesc = "NEW_ORDER")
    }

    /**
     * Dispatches automatic printing for direct counter orders entered on the POS.
     */
    suspend fun handleDirectPosOrder(payload: TicketPayload) = withContext(Dispatchers.IO) {
        if (!masterAutoPrintEnabled.value) {
            Log.d(tag, "Master auto-print is OFF. Skipping direct order print for ${payload.orderId}.")
            return@withContext
        }

        val targets = printers.value.filter {
            it.role != PrinterRole.DISABLED &&
                    (it.trigger == PrintTrigger.ON_DIRECT_POS_ORDER || it.trigger == PrintTrigger.ALWAYS_AUTOMATIC)
        }

        val unprintedTargets = targets.filter {
            !journalStore.isDestinationPrinted(payload.orderId, "${it.id}_${it.role}")
        }

        if (unprintedTargets.isEmpty() && targets.isNotEmpty()) {
            Log.d(tag, "All targets for direct order ${payload.orderId} were already printed. Skipping.")
            return@withContext
        }

        dispatchToPrinters(payload, unprintedTargets, eventDesc = "DIRECT_POS_ORDER")
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
            val destination = "${config.id}_${config.role}"
            try {
                val driver = getDriverForConfig(config)
                val bytes = EscPosTicketRenderer.renderTicket(payload.copy(copies = config.copies), config.role)
                val result = driver.printRaw(bytes)
                if (result is PrintResult.Success) {
                    journalStore.recordStatus(
                        payload.orderId,
                        destination,
                        com.komanda.business.hardware.printing.model.PrintStatus.PRINTED
                    )
                    Log.i(tag, "Auto-printed [$eventDesc] on '${config.name}' (${config.role}): $result")
                } else {
                    journalStore.recordStatus(
                        payload.orderId,
                        destination,
                        com.komanda.business.hardware.printing.model.PrintStatus.FAILED
                    )
                    Log.w(tag, "Failed auto-printing on '${config.name}': $result")
                }
            } catch (e: Exception) {
                journalStore.recordStatus(
                    payload.orderId,
                    destination,
                    com.komanda.business.hardware.printing.model.PrintStatus.UNCERTAIN
                )
                Log.e(tag, "Uncertain error auto-printing on '${config.name}'", e)
            }
        }
    }
}
