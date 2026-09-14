package com.komanda.business.hardware.printing

/**
 * Event triggers that determine when a printer automatically outputs a ticket.
 */
enum class PrintTrigger {
    /**
     * Automatically prints when a new incoming order is received (web/app/SSE).
     */
    ON_NEW_ORDER,

    /**
     * Automatically prints when an order is created directly by the cashier on the POS.
     */
    ON_DIRECT_POS_ORDER,

    /**
     * Automatically prints on both incoming online orders and direct POS orders.
     */
    ALWAYS_AUTOMATIC,

    /**
     * Never prints automatically; only outputs tickets when manually requested by the operator.
     */
    MANUAL_ONLY
}
