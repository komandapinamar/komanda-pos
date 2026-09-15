package com.komanda.business.hardware.printing.model

import android.content.Context
import android.content.SharedPreferences
import com.komanda.business.hardware.printing.enums.PrintTrigger
import com.komanda.business.hardware.printing.enums.PrinterRole
import com.komanda.business.hardware.printing.enums.PrinterType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Configuration profile for a registered printer.
 */
data class PrinterConfig(
    val id: String,
    val name: String,
    val type: PrinterType,
    val role: PrinterRole = PrinterRole.COUNTER,
    val trigger: PrintTrigger = PrintTrigger.ALWAYS_AUTOMATIC,
    val address: String? = null,
    val copies: Int = 1
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("type", type.name)
        put("role", role.name)
        put("trigger", trigger.name)
        put("address", address ?: "")
        put("copies", copies)
    }

    companion object {
        fun fromJson(json: JSONObject): PrinterConfig {
            val typeStr = json.optString("type", PrinterType.USB_ESC_POS.name)
            val roleStr = json.optString("role", PrinterRole.COUNTER.name)
            val triggerStr = json.optString("trigger", PrintTrigger.ALWAYS_AUTOMATIC.name)

            return PrinterConfig(
                id = json.getString("id"),
                name = json.optString("name", "Impresora Térmica"),
                type = runCatching { PrinterType.valueOf(typeStr) }.getOrDefault(PrinterType.USB_ESC_POS),
                role = runCatching { PrinterRole.valueOf(roleStr) }.getOrDefault(PrinterRole.COUNTER),
                trigger = runCatching { PrintTrigger.valueOf(triggerStr) }.getOrDefault(PrintTrigger.ALWAYS_AUTOMATIC),
                address = json.optString("address").takeIf { it.isNotBlank() },
                copies = json.optInt("copies", 1).coerceAtLeast(1)
            )
        }
    }
}

/**
 * Persists and observes printer routing configurations and the master auto-print switch.
 */
class PrinterConfigRepository(
    private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("komanda_pos_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PRINTERS = "configured_printers_json"
        private const val KEY_MASTER_AUTO_PRINT = "master_auto_print_enabled"
        const val DEFAULT_USB_ID = "usb_internal"
    }

    private val _printers = MutableStateFlow<List<PrinterConfig>>(emptyList())
    val printers: StateFlow<List<PrinterConfig>> = _printers.asStateFlow()

    private val _masterAutoPrintEnabled = MutableStateFlow(false)
    val masterAutoPrintEnabled: StateFlow<Boolean> = _masterAutoPrintEnabled.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        _masterAutoPrintEnabled.value = prefs.getBoolean(KEY_MASTER_AUTO_PRINT, false)

        val rawJson = prefs.getString(KEY_PRINTERS, null)
        if (rawJson.isNullOrBlank()) {
            // Auto-register default internal Telpo printer on first launch
            val defaultList = listOf(
                PrinterConfig(
                    id = DEFAULT_USB_ID,
                    name = "Impresora Térmica Telpo",
                    type = PrinterType.USB_ESC_POS,
                    role = PrinterRole.COUNTER,
                    trigger = PrintTrigger.ALWAYS_AUTOMATIC,
                    copies = 1
                )
            )
            _printers.value = defaultList
            persistPrinters(defaultList)
        } else {
            try {
                val array = JSONArray(rawJson)
                val list = mutableListOf<PrinterConfig>()
                for (i in 0 until array.length()) {
                    list.add(PrinterConfig.fromJson(array.getJSONObject(i)))
                }
                _printers.value = list
            } catch (_: Exception) {
                _printers.value = emptyList()
            }
        }
    }

    fun setMasterAutoPrint(enabled: Boolean) {
        _masterAutoPrintEnabled.value = enabled
        prefs.edit().putBoolean(KEY_MASTER_AUTO_PRINT, enabled).apply()
    }

    fun savePrinter(config: PrinterConfig) {
        val current = _printers.value.toMutableList()
        val index = current.indexOfFirst { it.id == config.id }
        if (index >= 0) {
            current[index] = config
        } else {
            current.add(config)
        }
        _printers.value = current
        persistPrinters(current)
    }

    fun removePrinter(id: String) {
        val updated = _printers.value.filter { it.id != id }
        _printers.value = updated
        persistPrinters(updated)
    }

    private fun persistPrinters(list: List<PrinterConfig>) {
        val array = JSONArray()
        for (item in list) {
            array.put(item.toJson())
        }
        prefs.edit().putString(KEY_PRINTERS, array.toString()).apply()
    }
}
