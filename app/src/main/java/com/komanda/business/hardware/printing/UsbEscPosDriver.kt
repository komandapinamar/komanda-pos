package com.komanda.business.hardware.printing

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.dantsu.escposprinter.connection.usb.UsbConnection
import com.dantsu.escposprinter.connection.usb.UsbPrintersConnections
import com.komanda.business.core.model.TicketPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * High-performance USB ESC/POS printer driver powered by DantSu ESCPOS-ThermalPrinter-Android.
 * Automatically discovers connected USB thermal printers (such as internal Telpo TPS680/TPS900
 * Printer-80, or external desktop/POS thermal printers via USB Class 7).
 */
class UsbEscPosDriver(
    private val context: Context
) : PrinterDriver {

    override val type: PrinterType = PrinterType.USB_ESC_POS
    override val name: String
        get() {
            val device = getConnectedDevice()
            val prodName = device?.productName?.takeIf { it.isNotBlank() } ?: "Impresora Térmica USB"
            return "$prodName (${device?.vendorId?.toString(16) ?: "?"}:${device?.productId?.toString(16) ?: "?"})"
        }

    private val tag = "UsbEscPosDriver"

    companion object {
        const val ACTION_USB_PERMISSION = "com.komanda.business.USB_PERMISSION"
    }

    private val usbManager: UsbManager?
        get() = context.getSystemService(Context.USB_SERVICE) as? UsbManager

    /**
     * Returns the first connected USB thermal printer device, if any.
     */
    fun getConnectedDevice(): UsbDevice? {
        val connection = UsbPrintersConnections.selectFirstConnected(context)
        return connection?.device
    }

    /**
     * Checks if permission is currently granted for the connected USB printer.
     */
    fun hasPermission(): Boolean {
        val device = getConnectedDevice() ?: return false
        val mgr = usbManager ?: return false
        return mgr.hasPermission(device)
    }

    /**
     * Requests USB permission dialog for the connected printer.
     */
    fun requestPermission() {
        val device = getConnectedDevice() ?: return
        val mgr = usbManager ?: return
        if (!mgr.hasPermission(device)) {
            Log.i(tag, "Requesting USB permission for ${device.productName ?: device.deviceName}")
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val intent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(ACTION_USB_PERMISSION),
                flags
            )
            mgr.requestPermission(device, intent)
        }
    }

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        val connection = UsbPrintersConnections.selectFirstConnected(context)
        connection != null
    }

    override suspend fun printTicket(payload: TicketPayload): PrintResult {
        val bytes = EscPosTicketRenderer.renderTicket(payload)
        return printRaw(bytes)
    }

    override suspend fun printRaw(bytes: ByteArray): PrintResult = withContext(Dispatchers.IO) {
        val connection: UsbConnection? = UsbPrintersConnections.selectFirstConnected(context)
        if (connection == null) {
            Log.w(tag, "No USB thermal printer detected.")
            return@withContext PrintResult.Error(
                code = "NO_USB_PRINTER",
                message = "No se detectó ninguna impresora térmica USB conectada."
            )
        }

        val device = connection.device
        val mgr = usbManager
        if (mgr != null && !mgr.hasPermission(device)) {
            Log.w(tag, "Permission not granted for USB printer ${device.productName ?: device.deviceName}. Requesting...")
            requestPermission()
            return@withContext PrintResult.Error(
                code = "USB_PERMISSION_DENIED",
                message = "Permiso USB pendiente para impresora: ${device.productName ?: device.deviceName}"
            )
        }

        try {
            Log.d(tag, "Connecting to USB printer ${device.productName ?: device.deviceName} (payload: ${bytes.size} bytes)...")
            connection.connect()
            connection.write(bytes)
            connection.send()
            Log.i(tag, "Ticket printed successfully via USB ESC/POS (${bytes.size} bytes).")
            PrintResult.Success
        } catch (e: Exception) {
            Log.e(tag, "Error printing on USB printer: ${e.message}", e)
            PrintResult.Error(
                code = "USB_PRINT_ERROR",
                message = "Error en impresora térmica USB: ${e.message}",
                cause = e
            )
        } finally {
            try {
                connection.disconnect()
            } catch (closeEx: Exception) {
                Log.w(tag, "Error closing USB connection", closeEx)
            }
        }
    }
}
