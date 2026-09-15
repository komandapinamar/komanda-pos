package com.komanda.business.hardware.printing.drivers

import android.bluetooth.BluetoothManager
import android.content.Context
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection
import com.komanda.business.core.model.TicketPayload
import com.komanda.business.hardware.printing.renderer.EscPosTicketRenderer
import com.komanda.business.hardware.printing.enums.PrinterType
import com.komanda.business.hardware.printing.model.PrintResult
import com.komanda.business.hardware.printing.PrinterDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bluetooth ESC/POS printer driver powered by DantSu ESCPOS-ThermalPrinter-Android (BluetoothConnection).
 */
class BluetoothEscPosDriver(
    private val context: Context,
    private val macAddress: String,
    private val deviceName: String = "Impresora Bluetooth"
) : PrinterDriver {

    override val type: PrinterType = PrinterType.BLUETOOTH_ESC_POS
    override val name: String = "$deviceName ($macAddress)"

    private val bluetoothManager: BluetoothManager?
        get() = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        val adapter = bluetoothManager?.adapter ?: return@withContext false
        if (!adapter.isEnabled) return@withContext false
        try {
            val device = adapter.getRemoteDevice(macAddress)
            device != null
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun printTicket(payload: TicketPayload): PrintResult {
        val bytes = EscPosTicketRenderer.renderTicket(payload)
        return printRaw(bytes)
    }

    override suspend fun printRaw(bytes: ByteArray): PrintResult = withContext(Dispatchers.IO) {
        val adapter = bluetoothManager?.adapter
            ?: return@withContext PrintResult.Error("NO_BLUETOOTH", "El dispositivo no posee adaptador Bluetooth.")

        try {
            val device = adapter.getRemoteDevice(macAddress)
            val connection = BluetoothConnection(device)
            connection.connect()
            connection.write(bytes)
            connection.send()
            connection.disconnect()
            PrintResult.Success
        } catch (e: Exception) {
            PrintResult.Error(
                code = "BLUETOOTH_PRINT_FAILED",
                message = "Error imprimiendo por Bluetooth a $macAddress: ${e.message}",
                cause = e
            )
        }
    }
}
