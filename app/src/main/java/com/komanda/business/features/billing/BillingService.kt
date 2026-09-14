package com.komanda.business.features.billing

import com.komanda.business.core.model.AdminDashboardOrder
import com.komanda.business.core.model.FiscalInvoiceData
import com.komanda.business.core.network.CreateInvoiceRequest
import com.komanda.business.core.network.KomandaApi
import com.komanda.business.hardware.printing.PrintResult
import com.komanda.business.hardware.printing.PrinterRole
import com.komanda.business.hardware.printing.PrinterRouter

sealed class BillingResult {
    data class Success(val fiscalData: FiscalInvoiceData, val printResult: PrintResult) : BillingResult()
    data class Error(val code: String, val message: String) : BillingResult()
}

class BillingService(
    private val api: KomandaApi,
    private val printerRouter: PrinterRouter,
    private val tenantName: String = "Komanda"
) {

    suspend fun invoiceOrder(
        tenantId: String,
        order: AdminDashboardOrder,
        invoiceType: String = "B",
        docType: String = "FINAL",
        docNumber: String? = null
    ): BillingResult {
        return try {
            val response = api.createInvoice(
                tenantId = tenantId,
                orderId = order.id,
                body = CreateInvoiceRequest(
                    invoiceType = invoiceType,
                    docType = docType,
                    docNumber = docNumber
                )
            )

            if (!response.isSuccessful || response.body() == null) {
                val errorMsg = response.errorBody()?.string() ?: "Error de facturación AFIP"
                return BillingResult.Error(
                    code = "AFIP_INVOICE_FAILED",
                    message = errorMsg
                )
            }

            val invoice = response.body()!!
            val fiscalData = FiscalInvoiceData(
                invoiceType = invoice.invoiceType,
                pointOfSale = invoice.pointOfSale,
                invoiceNumber = invoice.invoiceNumber,
                cae = invoice.cae,
                caeDueDate = invoice.caeDueDate,
                qrData = invoice.qrData
            )

            val ticketPayload = order.toTicketPayload(tenantName = tenantName, copies = 1).copy(
                fiscalInfo = fiscalData
            )

            // Invoices are printed on the counter printer (or first available active printer)
            val counterPrinters = printerRouter.printers.value.filter { it.role == PrinterRole.COUNTER }
            val targetId = counterPrinters.firstOrNull()?.id
            val results = printerRouter.printManual(ticketPayload, targetPrinterId = targetId)
            val printResult = results.values.firstOrNull() ?: PrintResult.Success

            BillingResult.Success(
                fiscalData = fiscalData,
                printResult = printResult
            )
        } catch (e: Exception) {
            BillingResult.Error(
                code = "NETWORK_ERROR",
                message = "Error comunicando con el servidor para facturar: ${e.message}"
            )
        }
    }
}
