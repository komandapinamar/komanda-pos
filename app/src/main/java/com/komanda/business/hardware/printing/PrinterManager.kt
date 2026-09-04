package com.komanda.business.hardware.printing

import com.komanda.business.core.model.TicketPayload

class PrinterManager(
    private val defaultDriver: PrinterDriver,
    private val secondaryDrivers: List<PrinterDriver> = emptyList()
) {

    suspend fun printReceipt(payload: TicketPayload): PrintResult {
        // Print on primary / default driver
        val primaryResult = defaultDriver.printTicket(payload)

        // If configured, also mirror or route to secondary printers (e.g. kitchen network printer)
        for (driver in secondaryDrivers) {
            try {
                if (driver.isAvailable()) {
                    driver.printTicket(payload)
                }
            } catch (_: Exception) {
                // Secondary print failures don't block the primary checkout flow
            }
        }

        return primaryResult
    }
}
