package com.komanda.business.features.orders

import android.util.Log
import com.komanda.business.core.audio.OrderAnnouncer
import com.komanda.business.core.model.AdminDashboardOrder
import com.komanda.business.core.model.OrderStatuses
import com.komanda.business.core.network.ConnectionState
import com.komanda.business.core.network.KomandaApi
import com.komanda.business.core.network.SseOrderEventListener
import com.komanda.business.core.network.TransitionOrderRequest
import com.komanda.business.hardware.printing.model.PrinterConfig
import com.komanda.business.hardware.printing.PrinterRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class OrderManager(
    private val tenantId: String,
    private val api: KomandaApi,
    private val sseListener: SseOrderEventListener,
    private val announcer: OrderAnnouncer? = null,
    val printerRouter: PrinterRouter? = null,
    private val tenantName: String = "Komanda",
    private val autoPrint: suspend (AdminDashboardOrder) -> Unit = { order ->
        printerRouter?.handleNewOrder(order.toTicketPayload(tenantName = tenantName))
    }
) {

    private val tag = "OrderManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val orderMutex = Mutex()
    private var running = false
    private var snapshotJob: Job? = null

    private val _snapshotState = MutableStateFlow<SnapshotState>(SnapshotState.Loading)
    val snapshotState: StateFlow<SnapshotState> = _snapshotState.asStateFlow()

    private val _orders = MutableStateFlow<List<AdminDashboardOrder>>(emptyList())
    val orders: StateFlow<List<AdminDashboardOrder>> = _orders.asStateFlow()

    private val _lastUpdatedAt = MutableStateFlow<String?>(null)
    val lastUpdatedAt: StateFlow<String?> = _lastUpdatedAt.asStateFlow()

    private val _transitioningOrderId = MutableStateFlow<String?>(null)
    val transitioningOrderId: StateFlow<String?> = _transitioningOrderId.asStateFlow()

    val connectionState: StateFlow<ConnectionState> = sseListener.connectionState

    val masterAutoPrintEnabled: StateFlow<Boolean> =
        printerRouter?.masterAutoPrintEnabled ?: MutableStateFlow(true)

    val configuredPrinters: StateFlow<List<PrinterConfig>> =
        printerRouter?.printers ?: MutableStateFlow(emptyList())

    // Track processed order IDs so existing orders on startup are not auto-printed
    private val seenOrderIds = mutableSetOf<String>()
    init {
        sseListener.applyEvent = { event -> refreshSingleOrder(event.orderId) }
        sseListener.reconcile = { loadSnapshot() }
    }

    fun startListening() {
        if (running) return
        running = true
        _snapshotState.value = SnapshotState.Loading
        sseListener.start()
        refreshOrders()
    }

    fun stopListening() {
        running = false
        snapshotJob?.cancel()
        sseListener.stop()
    }

    fun setMasterAutoPrint(enabled: Boolean) {
        printerRouter?.setMasterAutoPrint(enabled)
    }

    fun refreshOrders() {
        snapshotJob?.cancel()
        snapshotJob = scope.launch {
            _snapshotState.value = SnapshotState.Loading
            repeat(4) { attempt ->
                if (loadSnapshot()) return@launch
                if (attempt < 3) delay((1_000L shl attempt) + kotlin.random.Random.nextLong(500))
            }
        }
    }

    private suspend fun loadSnapshot(): Boolean {
        return orderMutex.withLock { try {
            // Fetch all pages so an active order outside the first 50 is not lost.
            val list = mutableListOf<AdminDashboardOrder>()
            var cursor: String? = null
            val cursors = mutableSetOf<String>()
            var pages = 0
            do {
                if (pages++ >= MAX_SNAPSHOT_PAGES) {
                    Log.w(tag, "Snapshot exceeded $MAX_SNAPSHOT_PAGES pages; aborting.")
                    _snapshotState.value = SnapshotState.Error
                    return false
                }
                val response = api.listOrders(tenantId, cursor = cursor)
                if (!response.isSuccessful || response.body() == null) {
                    _snapshotState.value = SnapshotState.Error
                    return false
                }
                val body = response.body()!!
                list.addAll(body.data.map { it.toDashboardOrder() })
                cursor = body.nextCursor
                if (cursor != null && !cursors.add(cursor)) {
                    _snapshotState.value = SnapshotState.Error
                    return false
                }
            } while (cursor != null)
            val active = list.filter { it.isActive() }.distinctBy { it.id }
            seenOrderIds.addAll(list.map { it.id })
            _orders.value = active.sortedByDescending { it.updatedAt }
            _lastUpdatedAt.value = currentIsoDate()
            _snapshotState.value = SnapshotState.Ready
            true
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(tag, "Unable to reconcile orders", e)
            _snapshotState.value = SnapshotState.Error
            false
        } }
    }

    private suspend fun refreshSingleOrder(orderId: String): Boolean {
        return try {
            // Serialize with the snapshot, including its HTTP fetch.
            val missing = orderMutex.withLock {
                val response = api.getOrder(tenantId, orderId)
                if (response.isSuccessful && response.body() != null) {
                    val dashboardOrder = response.body()!!.toDashboardOrder()
                    val rest = _orders.value.filter { it.id != dashboardOrder.id }
                    _orders.value = (if (dashboardOrder.isActive()) listOf(dashboardOrder) + rest else rest)
                        .sortedByDescending { it.updatedAt }
                    _lastUpdatedAt.value = currentIsoDate()
                    // Only a first live event after a successful snapshot may print.
                    if (seenOrderIds.add(orderId) && running && _snapshotState.value == SnapshotState.Ready &&
                        dashboardOrder.status == OrderStatuses.APPROVED) onNewOrderArrived(dashboardOrder)
                    return true
                }
                if (response.code() != 404) return false
                true
            }
            // A deleted/inaccessible detail can only be considered applied after
            // an authoritative snapshot has repaired the active list.
            if (missing) loadSnapshot() else false
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(tag, "Unable to apply order event", e)
            false
        }
    }

    private fun onNewOrderArrived(order: AdminDashboardOrder) {
        scope.launch {
            runCatching { autoPrint(order) }
                .onFailure { Log.w(tag, "Auto-print failed for order ${order.id}", it) }
        }
    }

    fun transitionOrder(order: AdminDashboardOrder) {
        val version = order.version ?: return
        val targetStatus = AdminDashboardOrder.nextStatus(order.status) ?: return

        _transitioningOrderId.value = order.id

        scope.launch {
            try {
                val pinToSend = if (targetStatus == OrderStatuses.DELIVERED) order.pickupPin else null
                val response = api.transitionOrderStatus(
                    tenantId = tenantId,
                    orderId = order.id,
                    version = version.toString(),
                    body = TransitionOrderRequest(
                        fulfillmentStatus = targetStatus,
                        pickupPin = pinToSend
                    )
                )

                if (response.isSuccessful && response.body() != null) {
                    val updated = response.body()!!.toDashboardOrder()
                    orderMutex.withLock {
                        _orders.value = _orders.value.mapNotNull {
                            if (it.id != updated.id) it else updated.takeIf { it.isActive() }
                        }
                    }
                    _lastUpdatedAt.value = currentIsoDate()

                    // Voice announcement on speaker
                    if (targetStatus == OrderStatuses.READY) {
                        announcer?.announceOrderReady(updated.purchaseNumber, updated.customer.name)
                    } else if (targetStatus == OrderStatuses.DELIVERED) {
                        announcer?.announceOrderDelivered(updated.purchaseNumber, updated.customer.name)
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

    suspend fun printOrderTicket(order: AdminDashboardOrder, targetPrinterId: String? = null) {
        val router = printerRouter ?: return
        val payload = order.toTicketPayload(tenantName = tenantName)
        router.printManual(payload, targetPrinterId = targetPrinterId)
    }

    fun announceOrderReady(purchaseNumber: String, clientName: String? = null) {
        announcer?.announceOrderReady(purchaseNumber, clientName)
    }

    private fun currentIsoDate(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return format.format(Date())
    }
}

sealed interface SnapshotState {
    data object Loading : SnapshotState
    data object Ready : SnapshotState
    data object Error : SnapshotState
}

private fun AdminDashboardOrder.isActive() = status in setOf(
    OrderStatuses.APPROVED, OrderStatuses.PREPARING, OrderStatuses.READY
)

private const val MAX_SNAPSHOT_PAGES = 50
