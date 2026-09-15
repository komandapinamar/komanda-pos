package com.komanda.business.hardware.printing.model

sealed class PrintResult {
    data object Success : PrintResult()
    data class Error(val code: String, val message: String, val cause: Throwable? = null) : PrintResult()
}
