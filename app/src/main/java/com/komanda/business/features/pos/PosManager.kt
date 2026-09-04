package com.komanda.business.features.pos

import android.util.Log
import com.komanda.business.core.model.AdminDashboardOrder
import com.komanda.business.core.network.CatalogCategoryDto
import com.komanda.business.core.network.CatalogItemDto
import com.komanda.business.core.network.CreateDirectOrderRequest
import com.komanda.business.core.network.DirectOrderCustomerRequest
import com.komanda.business.core.network.DirectOrderItemRequest
import com.komanda.business.core.network.KomandaApi
import com.komanda.business.hardware.printing.PrinterManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

sealed class DirectOrderResult {
    data class Success(val order: AdminDashboardOrder) : DirectOrderResult()
    data class Error(val message: String) : DirectOrderResult()
}

class PosManager(
    private val tenantId: String,
    private val api: KomandaApi,
    private val printerManager: PrinterManager? = null,
    private val tenantName: String = "Komanda"
) {

    private val tag = "PosManager"

    private val _categories = MutableStateFlow<List<CatalogCategoryDto>>(emptyList())
    val categories: StateFlow<List<CatalogCategoryDto>> = _categories.asStateFlow()

    private val _items = MutableStateFlow<List<CatalogItemDto>>(emptyList())
    val items: StateFlow<List<CatalogItemDto>> = _items.asStateFlow()

    private val _quantities = MutableStateFlow<Map<String, Int>>(emptyMap())
    val quantities: StateFlow<Map<String, Int>> = _quantities.asStateFlow()

    private val _customerName = MutableStateFlow("")
    val customerName: StateFlow<String> = _customerName.asStateFlow()

    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes.asStateFlow()

    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()

    suspend fun loadCatalog() {
        try {
            val catRes = api.listCategories(tenantId)
            if (catRes.isSuccessful && catRes.body() != null) {
                _categories.value = catRes.body()!!.data
            }

            val itemRes = api.listItems(tenantId)
            if (itemRes.isSuccessful && itemRes.body() != null) {
                _items.value = itemRes.body()!!.data.filter { it.status == "active" }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to load catalog", e)
        }
    }

    fun handleQuantityChange(itemId: String, value: Int) {
        val next = _quantities.value.toMutableMap()
        if (value <= 0) {
            next.remove(itemId)
        } else {
            next[itemId] = value.coerceAtMost(50)
        }
        _quantities.value = next
    }

    fun setCustomerName(name: String) {
        _customerName.value = name
    }

    fun setNotes(notes: String) {
        _notes.value = notes
    }

    fun clearForm() {
        _quantities.value = emptyMap()
        _customerName.value = ""
        _notes.value = ""
    }

    val selectedCount: Int
        get() = _quantities.value.values.sumOf { if (it > 0) 1 as Int else 0 as Int }

    suspend fun submitDirectOrder(): DirectOrderResult {
        if (selectedCount == 0) {
            return DirectOrderResult.Error("Seleccioná al menos un producto.")
        }
        if (_customerName.value.trim().isBlank()) {
            return DirectOrderResult.Error("Ingresá el nombre del cliente.")
        }

        _submitting.value = true

        val requestItems = _quantities.value
            .filter { it.value > 0 }
            .map { (resourceId, quantity) ->
                DirectOrderItemRequest(
                    kind = "item",
                    resourceId = resourceId,
                    quantity = quantity
                )
            }

        val body = CreateDirectOrderRequest(
            items = requestItems,
            customer = DirectOrderCustomerRequest(name = _customerName.value.trim()),
            notes = _notes.value.trim().ifBlank { null }
        )

        return try {
            val idempotencyKey = UUID.randomUUID().toString()
            val response = api.createDirectOrder(
                tenantId = tenantId,
                idempotencyKey = idempotencyKey,
                body = body
            )

            if (!response.isSuccessful || response.body() == null) {
                val errorMsg = response.errorBody()?.string() ?: "Error al crear el pedido."
                _submitting.value = false
                return DirectOrderResult.Error(errorMsg)
            }

            val order = response.body()!!.toDashboardOrder()

            // Automatic print of direct order (2 copies: COCINA + CAJA / ENTREGA)
            printerManager?.let { pm ->
                try {
                    val ticketPayload = order.toTicketPayload(tenantName = tenantName, copies = 2)
                    pm.printReceipt(ticketPayload)
                } catch (e: Exception) {
                    Log.e(tag, "Print error after creating direct order", e)
                }
            }

            clearForm()
            _submitting.value = false
            DirectOrderResult.Success(order)
        } catch (e: Exception) {
            Log.e(tag, "Error submitting direct order", e)
            _submitting.value = false
            DirectOrderResult.Error(e.message ?: "Error al crear el pedido.")
        }
    }
}
