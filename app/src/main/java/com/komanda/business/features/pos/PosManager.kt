package com.komanda.business.features.pos

import android.util.Log
import com.komanda.business.core.model.AdminDashboardOrder
import com.komanda.business.core.model.orderTrackingUrl
import com.komanda.business.core.network.CatalogCategoryDto
import com.komanda.business.core.network.CatalogItemDto
import com.komanda.business.core.network.CreateDirectOrderRequest
import com.komanda.business.core.network.DirectOrderCustomerRequest
import com.komanda.business.core.network.DirectOrderItemRequest
import com.komanda.business.core.network.KomandaApi
import com.komanda.business.hardware.printing.PrinterRouter
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
    private val printerRouter: PrinterRouter? = null,
    private val tenantName: String = "Komanda",
    private val baseUrl: String = "",
    private val attemptStore: CheckoutAttemptStore? = null
) {

    private val tag = "PosManager"

    private val _categories = MutableStateFlow<List<CatalogCategoryDto>>(emptyList())
    val categories: StateFlow<List<CatalogCategoryDto>> = _categories.asStateFlow()

    private val _items = MutableStateFlow<List<CatalogItemDto>>(emptyList())
    val items: StateFlow<List<CatalogItemDto>> = _items.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _quantities = MutableStateFlow<Map<String, Int>>(emptyMap())
    val quantities: StateFlow<Map<String, Int>> = _quantities.asStateFlow()

    private val _customerName = MutableStateFlow("")
    val customerName: StateFlow<String> = _customerName.asStateFlow()

    private val _discountCode = MutableStateFlow("")
    val discountCode: StateFlow<String> = _discountCode.asStateFlow()

    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes.asStateFlow()

    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()

    suspend fun loadCatalog() {
        _isLoading.value = true
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
        } finally {
            _isLoading.value = false
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

    fun setDiscountCode(code: String) {
        _discountCode.value = code
    }

    fun setNotes(notes: String) {
        _notes.value = notes
    }

    fun clearForm() {
        _quantities.value = emptyMap()
        _customerName.value = ""
        _discountCode.value = ""
        _notes.value = ""
    }

    val selectedCount: Int
        get() = _quantities.value.values.sum()

    suspend fun submitDirectOrder(): DirectOrderResult {
        if (selectedCount == 0) {
            return DirectOrderResult.Error("Seleccioná al menos un producto.")
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

        val customerName = _customerName.value.trim().ifBlank { "NN" }
        val discountCode = _discountCode.value.trim().ifBlank { null }
        val notes = _notes.value.trim().ifBlank { null }
        val body = CreateDirectOrderRequest(
            items = requestItems,
            customer = DirectOrderCustomerRequest(name = customerName),
            notes = notes,
            discountCode = discountCode
        )

        val itemsList = _quantities.value
            .filter { it.value > 0 }
            .map { it.key to it.value }
        val cartHash = CheckoutAttemptStore.computeCartHash(itemsList, customerName, notes, discountCode)
        val attempt = attemptStore?.getOrStartAttempt(tenantId, cartHash)
        val idempotencyKey = attempt?.idempotencyKey ?: UUID.randomUUID().toString()

        return try {
            val response = api.createDirectOrder(
                tenantId = tenantId,
                idempotencyKey = idempotencyKey,
                body = body
            )

            if (!response.isSuccessful || response.body() == null) {
                val errorMsg = response.errorBody()?.string() ?: "Error al crear el pedido."
                attempt?.let { attemptStore?.markAttemptUnknown(tenantId, it) }
                _submitting.value = false
                return DirectOrderResult.Error(errorMsg)
            }

            val order = response.body()!!.toDashboardOrder()
            attempt?.let {
                attemptStore?.markAttemptConfirmed(tenantId, it, order.id)
                attemptStore?.clearConfirmedAttempt(tenantId)
            }

            // Dispatches automatic printing according to configured printer triggers
            printerRouter?.let { router ->
                try {
                    val ticketPayload = order.toTicketPayload(tenantName = tenantName).copy(
                        trackingUrl = orderTrackingUrl(baseUrl, tenantId, order.id)
                    )
                    router.handleDirectPosOrder(ticketPayload)
                } catch (e: Exception) {
                    Log.e(tag, "Print error after creating direct order", e)
                }
            }

            clearForm()
            _submitting.value = false
            DirectOrderResult.Success(order)
        } catch (e: Exception) {
            Log.e(tag, "Error submitting direct order", e)
            attempt?.let { attemptStore?.markAttemptUnknown(tenantId, it) }
            _submitting.value = false
            DirectOrderResult.Error(e.message ?: "Error al crear el pedido.")
        }
    }
}
