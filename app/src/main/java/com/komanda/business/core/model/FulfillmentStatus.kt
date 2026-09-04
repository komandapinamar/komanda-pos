package com.komanda.business.core.model

enum class FulfillmentStatus(val wireValue: String) {
    APPROVED("approved"),
    PREPARING("preparing"),
    READY("ready"),
    DELIVERED("delivered"),
    CANCELLED("cancelled");

    fun isTerminal(): Boolean = this == DELIVERED || this == CANCELLED

    fun nextStatus(): FulfillmentStatus? = when (this) {
        APPROVED -> PREPARING
        PREPARING -> READY
        READY -> DELIVERED
        DELIVERED, CANCELLED -> null
    }

    fun nextStatusActionLabel(): String? = when (this) {
        APPROVED -> "Preparar"
        PREPARING -> "Marcar Listo"
        READY -> "Marcar Entregado"
        DELIVERED, CANCELLED -> null
    }

    fun canTransitionTo(next: FulfillmentStatus): Boolean {
        if (this == next) return true
        return when (this) {
            APPROVED -> next == PREPARING || next == CANCELLED
            PREPARING -> next == READY || next == CANCELLED
            READY -> next == DELIVERED
            DELIVERED, CANCELLED -> false
        }
    }

    companion object {
        fun fromWireValue(value: String?): FulfillmentStatus {
            return entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: APPROVED
        }
    }
}
