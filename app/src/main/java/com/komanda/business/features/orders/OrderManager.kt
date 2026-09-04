package com.komanda.business.features.orders

import android.util.Log
import com.komanda.business.core.audio.OrderAnnouncer
import com.komanda.business.core.model.AdminDashboardOrder
import com.komanda.business.core.model.OrderStatuses
import com.komanda.business.core.network.ConnectionState
import com.komanda.business.core.network.KomandaApi
import com.komanda.business.core.network.SseOrderEventListener
import com.komanda.business.core.network.TransitionOrderRequest
import com.komanda.business.hardware.printing.PrinterManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class OrderManager(
    private val tenantId: String,
    private val api: KomandaApi,
    private val sseListener: SseOrderEventListener,
    private val announcer: OrderAnnouncer,
    private val printerManager: PrinterManager? = null,
    private val tenantName: String = "Komanda"
) {

    private val tag = "OrderManager"
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _orders = MutableStateFlow<List<AdminDashboardOrder>>(emptyList())
    val orders: StateFlow<List<AdminDashboardOrder>> = _orders.asStateFlow()

    private val _lastUpdatedAt = MutableStateFlow<String?>(null)
    val lastUpdatedAt: StateFlow<String?> = _lastUpdatedAt.asStateFlow()

    private val _transitioningOrderId = MutableStateFlow<String?>(null)
    val transitioningOrderId: StateFlow<String?> = _transitioningOrderId.asStateFlow()

    val connectionState: StateFlow<ConnectionState> = sseListener.connectionState

    init {
        scope.launch {
            sseListener.events.collect { event ->
                Log.d(tag, "Received order event: ${event.orderId}, seq: ${event.sequence}")
                refreshSingleOrder(event.orderId)
            }
        }
    }

    fun startListening() {
        sseListener.start()
        refreshOrders()
    }

    fun stopListening() {
        sseListener.stop()
    }

    fun refreshOrders() {
        scope.launch {
            try {
                val response = api.listOrders(tenantId)
                if (response.isSuccessful && response.body() != null) {
                    val list = response.body()!!.data.map { it.toDashboardOrder() }
                    _orders.value = list.sortedByDescending { it.updatedAt }
                    _lastUpdatedAt.value = currentIsoDate()
                } else {
                    Log.e(tag, "Failed to load orders: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e(tag, "Network error loading orders", e)
            }
        }
    }

    private suspend fun refreshSingleOrder(orderId: String) {
        try {
            val response = api.getOrder(tenantId, orderId)
            if (response.isSuccessful && response.body() != null) {
                val dashboardOrder = response.body()!!.toDashboardOrder()
                val rest = _orders.value.filter { it.id != dashboardOrder.id }
                _orders.value = (listOf(dashboardOrder) + rest).sortedByDescending { it.updatedAt }
                _lastUpdatedAt.value = currentIsoDate()
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to refresh order $orderId", e)
        }
    }

    fun transitionOrder(order: AdminDashboardOrder) {
        val version = order.version ?: return
        val targetStatus = AdminDashboardOrder.nextStatus(order.status) ?: return

        _transitioningOrderId.value = order.id

        scope.launch {
            try {
                val response = api.transitionOrderStatus(
                    tenantId = tenantId,
                    orderId = order.id,
                    version = version.toString(),
                    body = TransitionOrderRequest(fulfillmentStatus = targetStatus)
                )

                if (response.isSuccessful && response.body() != null) {
                    val updated = response.body()!!.toDashboardOrder()
                    _orders.value = _orders.value.map { if (it.id == updated.id) updated else it }
                    _lastUpdatedAt.value = currentIsoDate()

                    // Voice announcement on speaker
                    if (targetStatus == OrderStatuses.READY) {
                        announcer.announceOrderReady(updated.purchaseNumber)
                    } else if (targetStatus == OrderStatuses.DELIVERED) {
                        announcer.announceOrderDelivered(updated.purchaseNumber)
                    }
                } else {
                    Log.e(tag, "Failed to transition order ${order.id}: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e(tag, "Error transitioning order ${order.id}", e)
            } finally {
                _transitioningOrderId.value = null
            }
        }
    }

    suspend fun printOrderTicket(order: AdminDashboardOrder) {
        val printer = printerManager ?: return
        val payload = order.toTicketPayload(tenantName = tenantName)
        printer.printReceipt(payload)
    }

    private fun currentIsoDate(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return format.format(Date())
    }
}
