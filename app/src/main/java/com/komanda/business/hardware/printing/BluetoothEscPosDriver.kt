package com.komanda.business.hardware.printing

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.komanda.business.core.model.TicketPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class BluetoothEscPosDriver(
    private val macAddress: String,
    private val deviceName: String = "Impresora Bluetooth"
) : PrinterDriver {

    override val type: PrinterType = PrinterType.BLUETOOTH_ESC_POS
    override val name: String = "$deviceName ($macAddress)"

    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return@withContext false
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
        val adapter = BluetoothAdapter.getDefaultAdapter()
            ?: return@withContext PrintResult.Error("NO_BLUETOOTH", "El dispositivo no posee adaptador Bluetooth.")

        var socket: BluetoothSocket? = null
        try {
            val device: BluetoothDevice = adapter.getRemoteDevice(macAddress)
            adapter.cancelDiscovery()

            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()

            socket.outputStream.apply {
                write(bytes)
                flush()
            }
            PrintResult.Success
        } catch (e: Exception) {
            PrintResult.Error(
                code = "BLUETOOTH_PRINT_FAILED",
                message = "Error imprimiendo por Bluetooth a $macAddress: ${e.message}",
                cause = e
            )
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }
}
