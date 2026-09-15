package com.komanda.business.hardware.printing.factory

import android.content.Context
import com.komanda.business.hardware.printing.PrinterDriver
import com.komanda.business.hardware.printing.drivers.BluetoothEscPosDriver
import com.komanda.business.hardware.printing.drivers.NetworkEscPosDriver
import com.komanda.business.hardware.printing.drivers.TelpoPrinterDriver
import com.komanda.business.hardware.printing.drivers.UsbEscPosDriver
import com.komanda.business.hardware.printing.enums.PrinterType
import com.komanda.business.hardware.printing.model.PrinterConfig

/**
 * Factory responsible for creating concrete [PrinterDriver] instances based on [PrinterConfig].
 */
class PrinterDriverFactory(
    private val context: Context
) {
    companion object {
        const val DEFAULT_NETWORK_PRINTER_ADDRESS = "192.168.1.100:9100"
        const val DEFAULT_NETWORK_PORT = 9100
    }

    /**
     * Resolves an executable [PrinterDriver] from a given configuration profile.
     */
    fun createDriver(config: PrinterConfig): PrinterDriver {
        return when (config.type) {
            PrinterType.USB_ESC_POS -> UsbEscPosDriver(context)
            PrinterType.NETWORK_ESC_POS -> {
                val addr = config.address ?: DEFAULT_NETWORK_PRINTER_ADDRESS
                val parts = addr.split(":")
                val host = parts[0].trim()
                val port = parts.getOrNull(1)?.toIntOrNull() ?: DEFAULT_NETWORK_PORT
                NetworkEscPosDriver(host = host, port = port)
            }
            PrinterType.BLUETOOTH_ESC_POS -> {
                BluetoothEscPosDriver(
                    context = context,
                    macAddress = config.address ?: "",
                    deviceName = config.name
                )
            }
            PrinterType.TELPO_INTERNAL -> TelpoPrinterDriver(context)
            else -> UsbEscPosDriver(context)
        }
    }
}
