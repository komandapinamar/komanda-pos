package com.komanda.business.hardware.printing.enums

/**
 * Functional role assigned to a printer in the POS ecosystem.
 */
enum class PrinterRole {
    /**
     * Kitchen printer for order preparation (no prices, no internal UUIDs, operational focus).
     */
    KITCHEN,

    /**
     * Counter / cash register printer for customer tickets (prices, subtotals, totals, branding).
     */
    COUNTER,

    /**
     * Device is registered but temporarily disabled.
     */
    DISABLED
}
