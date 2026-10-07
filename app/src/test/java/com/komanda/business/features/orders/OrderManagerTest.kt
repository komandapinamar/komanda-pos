package com.komanda.business.features.orders

import com.komanda.business.core.network.CollectCashRequest
import com.komanda.business.core.network.CollectCashResponse
import com.komanda.business.core.network.RefundCashRequest
import com.komanda.business.core.network.RefundCashResponse
import com.komanda.business.core.network.KomandaApi
import com.komanda.business.core.network.SseOrderEventListener
import com.komanda.business.core.network.TenantOrderEvent
import com.komanda.business.core.network.TenantOrderResponse
import com.komanda.business.core.network.TenantOrdersListResponse
import com.komanda.business.core.network.TransitionOrderRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class OrderManagerTest {
    private fun order(id: String, status: String) = TenantOrderResponse(
        id = id, purchaseNumber = "1", fulfillmentStatus = status,
        createdAt = "2026-09-23T12:00:00Z", updatedAt = "2026-09-23T12:00:00Z"
    )

    private fun api(
        list: () -> Response<TenantOrdersListResponse>,
        detail: (String) -> Response<TenantOrderResponse>
    ) = Proxy.newProxyInstance(KomandaApi::class.java.classLoader, arrayOf(KomandaApi::class.java)) { _, method, args ->
        when (method.name) {
            "listOrders" -> list()
            "getOrder" -> detail(args!![1] as String)
            else -> error("Unexpected API call: ${method.name}")
        }
    } as KomandaApi

    private fun stream(): SseOrderEventListener {
        val client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            okhttp3.Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(401).message("Unauthorized").body("".toResponseBody()).build()
        }).build()
        return SseOrderEventListener("https://example.test", "tenant", client = client)
    }

    private suspend fun eventually(predicate: () -> Boolean) {
        repeat(400) {
            if (predicate()) return
            delay(10)
        }
        assertTrue("Condition not reached", predicate())
    }

    @Test fun failedStartupShowsErrorThenManualRetryShowsAuthoritativeOrders() = runBlocking {
        val snapshot = AtomicReference<Response<TenantOrdersListResponse>>(Response.error(503, "offline".toResponseBody()))
        val manager = OrderManager("tenant", api({ snapshot.get() }, { Response.success(order(it, "approved")) }), stream())
        manager.refreshOrders()
        eventually { manager.snapshotState.value == SnapshotState.Error }
        assertTrue(manager.orders.value.isEmpty())
        assertEquals(null, manager.lastUpdatedAt.value) // no false authoritative empty state
        snapshot.set(Response.success(TenantOrdersListResponse(listOf(order("recovered", "approved")))))
        manager.refreshOrders()
        eventually { manager.snapshotState.value == SnapshotState.Ready }
        assertEquals(listOf("recovered"), manager.orders.value.map { it.id })
        snapshot.set(Response.success(TenantOrdersListResponse(emptyList())))
        manager.refreshOrders()
        eventually { manager.snapshotState.value == SnapshotState.Ready && manager.orders.value.isEmpty() }
        assertTrue(manager.lastUpdatedAt.value != null)
    }

    @Test fun foregroundResumeReconcilesWithoutPrintingHistoryAndPreservesOrdersOnError() = runBlocking {
        val snapshot = AtomicReference<Response<TenantOrdersListResponse>>(
            Response.success(TenantOrdersListResponse(listOf(order("old", "approved"))))
        )
        val printed = CopyOnWriteArrayList<String>()
        val manager = OrderManager("tenant", api({ snapshot.get() }, { Response.success(order(it, "approved")) }),
            stream(), autoPrint = { printed.add(it.id); Unit })
        manager.startListening()
        eventually { manager.orders.value.map { it.id } == listOf("old") }
        manager.stopListening()
        snapshot.set(Response.error(503, "offline".toResponseBody()))
        manager.startListening()
        eventually { manager.snapshotState.value == SnapshotState.Error }
        assertEquals(listOf("old"), manager.orders.value.map { it.id })
        manager.stopListening()
        snapshot.set(Response.success(TenantOrdersListResponse(listOf(order("missed", "preparing")))))
        manager.startListening()
        eventually { manager.snapshotState.value == SnapshotState.Ready && manager.orders.value.singleOrNull()?.id == "missed" }
        assertTrue(printed.isEmpty())
        manager.stopListening()
    }

    @Test fun overlappingSnapshotAndEventsAreSerializedAndNeverPrintTwice() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val snapshots = AtomicInteger()
        val details = AtomicInteger()
        val printed = CopyOnWriteArrayList<String>()
        val api = api({
            if (snapshots.incrementAndGet() == 2) {
                entered.countDown()
                assertTrue(release.await(3, TimeUnit.SECONDS))
            }
            Response.success(TenantOrdersListResponse(listOf(order("same", "approved"))))
        }, { id ->
            details.incrementAndGet()
            Response.success(order(id, if (id == "same") "ready" else "approved"))
        })
        val stream = stream()
        val manager = OrderManager("tenant", api, stream, autoPrint = { printed.add(it.id); Unit })
        manager.startListening()
        eventually { manager.snapshotState.value == SnapshotState.Ready }
        manager.refreshOrders()
        try {
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            val event = async(Dispatchers.Default) { stream.applyEvent(TenantOrderEvent("same", "1")) }
            delay(50)
            assertEquals(0, details.get()) // cannot overtake the in-flight snapshot
            release.countDown()
            assertTrue(event.await())
            assertEquals("ready", manager.orders.value.single().status)
            assertTrue(stream.applyEvent(TenantOrderEvent("same", "1")))
            assertEquals(1, manager.orders.value.size)
            assertTrue(printed.isEmpty())
            assertTrue(stream.applyEvent(TenantOrderEvent("new", "2")))
            eventually { printed == listOf("new") }
            assertTrue(stream.applyEvent(TenantOrderEvent("new", "2")))
            assertEquals(listOf("new"), printed.toList())
        } finally { release.countDown(); manager.stopListening() }
    }

    @Test fun failedDetailCannotApplyAndTerminalOrderLeavesActiveView() = runBlocking {
        val detail = AtomicReference<Response<TenantOrderResponse>>(Response.error(503, "offline".toResponseBody()))
        val stream = stream()
        val manager = OrderManager("tenant", api({
            Response.success(TenantOrdersListResponse(listOf(order("one", "approved"))))
        }, { detail.get() }), stream)
        manager.startListening()
        try {
            eventually { manager.snapshotState.value == SnapshotState.Ready }
            assertFalse(stream.applyEvent(TenantOrderEvent("one", "1")))
            assertEquals("approved", manager.orders.value.single().status)
            detail.set(Response.success(order("one", "delivered")))
            assertTrue(stream.applyEvent(TenantOrderEvent("one", "1")))
            assertTrue(manager.orders.value.isEmpty())
        } finally { manager.stopListening() }
    }

    @Test fun snapshotFollowsPaginationCursorToReachOrdersBeyondFirstPage() = runBlocking {
        val seenCursors = CopyOnWriteArrayList<String?>()
        val api = Proxy.newProxyInstance(KomandaApi::class.java.classLoader, arrayOf(KomandaApi::class.java)) { _, method, args ->
            when (method.name) {
                "listOrders" -> {
                    val cursor = args!![2] as String?
                    seenCursors.add(cursor)
                    if (cursor == null) {
                        Response.success(
                            TenantOrdersListResponse(listOf(order("page1", "approved")), "2026-09-23T12:00:00Z")
                        )
                    } else {
                        Response.success(TenantOrdersListResponse(listOf(order("page2", "preparing"))))
                    }
                }
                else -> error("Unexpected API call: ${method.name}")
            }
        } as KomandaApi
        val manager = OrderManager("tenant", api, stream())
        manager.refreshOrders()
        try {
            eventually { manager.snapshotState.value == SnapshotState.Ready }
            assertEquals(setOf("page1", "page2"), manager.orders.value.map { it.id }.toSet())
            assertEquals(listOf(null, "2026-09-23T12:00:00Z"), seenCursors.toList())
        } finally { manager.stopListening() }
    }

    @Test fun notFoundDetailRepairsSnapshotAndReportsEventApplied() = runBlocking {
        val detail = AtomicReference<Response<TenantOrderResponse>>(Response.error(404, "missing".toResponseBody()))
        val stream = stream()
        val manager = OrderManager("tenant", api({
            Response.success(TenantOrdersListResponse(listOf(order("gone", "cancelled"))))
        }, { detail.get() }), stream)
        manager.startListening()
        try {
            eventually { manager.snapshotState.value == SnapshotState.Ready }
            assertTrue(stream.applyEvent(TenantOrderEvent("gone", "5")))
            assertTrue(manager.orders.value.isEmpty())
        } finally { manager.stopListening() }
    }

    private fun cashOrder(id: String, paymentStatus: String = "pending", pickupPin: String = "1234") = TenantOrderResponse(
        id = id,
        purchaseNumber = "101",
        fulfillmentStatus = "approved",
        paymentStatus = paymentStatus,
        pickupPin = pickupPin,
        tender = "cash",
        source = "storefront_cash",
        createdAt = "2026-09-23T12:00:00Z",
        updatedAt = "2026-09-23T12:00:00Z"
    )

    private fun apiWithCollectCash(
        list: () -> Response<TenantOrdersListResponse> = { Response.success(TenantOrdersListResponse(emptyList())) },
        detail: (String) -> Response<TenantOrderResponse> = { Response.error(404, "".toResponseBody()) },
        transition: (String, String, String, TransitionOrderRequest) -> Response<TenantOrderResponse> = { _, _, _, _ -> error("unhandled transition") },
        collectCash: (String, String, CollectCashRequest) -> Response<CollectCashResponse> = { _, _, _ -> error("unhandled collectCash") },
        refundCash: (String, String, RefundCashRequest) -> Response<RefundCashResponse> = { _, _, _ -> error("unhandled refundCash") }
    ) = Proxy.newProxyInstance(KomandaApi::class.java.classLoader, arrayOf(KomandaApi::class.java)) { _, method, args ->
        when (method.name) {
            "listOrders" -> list()
            "getOrder" -> detail(args!![1] as String)
            "transitionOrderStatus" -> {
                val tenantId = args!![0] as String
                val orderId = args[1] as String
                val version = args[2] as String
                val body = args[3] as TransitionOrderRequest
                transition(tenantId, orderId, version, body)
            }
            "collectCash" -> {
                val orderId = args!![1] as String
                val idempotencyKey = args[2] as String
                val request = args[3] as CollectCashRequest
                collectCash(orderId, idempotencyKey, request)
            }
            "refundCash" -> {
                val orderId = args!![1] as String
                val idempotencyKey = args[2] as String
                val request = args[3] as RefundCashRequest
                refundCash(orderId, idempotencyKey, request)
            }
            else -> error("Unexpected API call: ${method.name}")
        }
    } as KomandaApi

    @Test fun collectCashWithPinSuccessUpdatesOrderToPaid() = runBlocking {
        val capturedKey = AtomicReference<String>()
        val capturedRequest = AtomicReference<CollectCashRequest>()
        val api = apiWithCollectCash(
            list = { Response.success(TenantOrdersListResponse(listOf(cashOrder("ord-1", "pending", "1234")))) },
            collectCash = { orderId, key, req ->
                capturedKey.set(key)
                capturedRequest.set(req)
                Response.success(
                    CollectCashResponse(
                        orderId = orderId,
                        purchaseNumber = "101",
                        paymentStatus = "paid",
                        paidAt = "2026-09-23T12:05:00Z"
                    )
                )
            }
        )
        val manager = OrderManager("tenant", api, stream())
        manager.refreshOrders()
        eventually { manager.snapshotState.value == SnapshotState.Ready }

        val pendingOrder = manager.orders.value.single()
        assertTrue(pendingOrder.isCashPending)
        assertEquals("pending", pendingOrder.paymentStatus)

        val result = manager.collectCash(pendingOrder, "pickup_pin", "1234")
        assertTrue("Expected Success, got $result", result is CollectCashResult.Success)
        val success = result as CollectCashResult.Success
        assertEquals("paid", success.order.paymentStatus)
        assertFalse(success.order.isCashPending)

        // Verify manager state is updated atomically
        val updatedInManager = manager.orders.value.single()
        assertEquals("paid", updatedInManager.paymentStatus)
        assertFalse(updatedInManager.isCashPending)

        // Verify request payload and idempotency key
        assertTrue(capturedKey.get().isNotBlank())
        assertEquals("pickup_pin", capturedRequest.get().authMethod)
        assertEquals("1234", capturedRequest.get().authCode)
    }

    @Test fun collectCashUsesServerVersionWhenReturned() = runBlocking {
        val api = apiWithCollectCash(
            list = { Response.success(TenantOrdersListResponse(listOf(cashOrder("ord-v", "pending", "1234")))) },
            collectCash = { orderId, _, _ ->
                Response.success(
                    CollectCashResponse(
                        orderId = orderId,
                        purchaseNumber = "101",
                        paymentStatus = "paid",
                        paidAt = "2026-09-23T12:05:00Z",
                        version = 7
                    )
                )
            }
        )
        val manager = OrderManager("tenant", api, stream())
        manager.refreshOrders()
        eventually { manager.snapshotState.value == SnapshotState.Ready }

        val pendingOrder = manager.orders.value.single()
        val result = manager.collectCash(pendingOrder, "pickup_pin", "1234")
        assertTrue(result is CollectCashResult.Success)
        // El version local debe reflejar el del servidor, no un incremento ciego
        assertEquals(7, (result as CollectCashResult.Success).order.version)
        assertEquals(7, manager.orders.value.single().version)
    }

    @Test fun collectCashWithAccountAuthSuccessUpdatesOrderToPaid() = runBlocking {
        val capturedRequest = AtomicReference<CollectCashRequest>()
        val api = apiWithCollectCash(
            list = { Response.success(TenantOrdersListResponse(listOf(cashOrder("ord-2", "pending", "5678")))) },
            collectCash = { orderId, _, req ->
                capturedRequest.set(req)
                Response.success(
                    CollectCashResponse(
                        orderId = orderId,
                        purchaseNumber = "102",
                        paymentStatus = "paid",
                        paidAt = "2026-09-23T12:05:00Z"
                    )
                )
            }
        )
        val manager = OrderManager("tenant", api, stream())
        manager.refreshOrders()
        eventually { manager.snapshotState.value == SnapshotState.Ready }

        val pendingOrder = manager.orders.value.single()
        val result = manager.collectCash(pendingOrder, "account_auth", null)
        assertTrue(result is CollectCashResult.Success)
        assertEquals("paid", (result as CollectCashResult.Success).order.paymentStatus)
        assertEquals("paid", manager.orders.value.single().paymentStatus)
        assertEquals("account_auth", capturedRequest.get().authMethod)
        assertEquals(null, capturedRequest.get().authCode)
    }

    @Test fun collectCashInvalidPinReturnsDescriptiveErrorAndPreservesPending() = runBlocking {
        val errorJson = """{"status":422,"title":"Invalid pickup PIN","code":"INVALID_PICKUP_PIN","detail":"El PIN ingresado es incorrecto."}"""
        val api = apiWithCollectCash(
            list = { Response.success(TenantOrdersListResponse(listOf(cashOrder("ord-3", "pending", "1234")))) },
            collectCash = { _, _, _ ->
                Response.error(422, errorJson.toResponseBody())
            }
        )
        val manager = OrderManager("tenant", api, stream())
        manager.refreshOrders()
        eventually { manager.snapshotState.value == SnapshotState.Ready }

        val pendingOrder = manager.orders.value.single()
        val result = manager.collectCash(pendingOrder, "pickup_pin", "9999")
        assertTrue(result is CollectCashResult.Error)
        assertEquals("El PIN ingresado es incorrecto.", (result as CollectCashResult.Error).message)

        // Order remains pending in manager
        val orderInManager = manager.orders.value.single()
        assertEquals("pending", orderInManager.paymentStatus)
        assertTrue(orderInManager.isCashPending)
    }

    @Test fun collectCashNoOpenShiftReturnsDescriptiveErrorAndPreservesPending() = runBlocking {
        val errorJson = """{"status":422,"title":"No open cash shift","code":"NO_OPEN_CASH_SHIFT","detail":"No existe un turno de caja abierto."}"""
        val api = apiWithCollectCash(
            list = { Response.success(TenantOrdersListResponse(listOf(cashOrder("ord-4", "pending", "1234")))) },
            collectCash = { _, _, _ ->
                Response.error(422, errorJson.toResponseBody())
            }
        )
        val manager = OrderManager("tenant", api, stream())
        manager.refreshOrders()
        eventually { manager.snapshotState.value == SnapshotState.Ready }

        val pendingOrder = manager.orders.value.single()
        val result = manager.collectCash(pendingOrder, "account_auth")
        assertTrue(result is CollectCashResult.Error)
        assertEquals("No hay un turno de caja abierto.", (result as CollectCashResult.Error).message)

        // Order remains pending
        assertEquals("pending", manager.orders.value.single().paymentStatus)
    }

    @Test fun collectCashNetworkFailureReturnsConnectionErrorAndPreservesPending() = runBlocking {
        val api = apiWithCollectCash(
            list = { Response.success(TenantOrdersListResponse(listOf(cashOrder("ord-5", "pending", "1234")))) },
            collectCash = { _, _, _ ->
                throw java.io.IOException("Network connection timeout")
            }
        )
        val manager = OrderManager("tenant", api, stream())
        manager.refreshOrders()
        eventually { manager.snapshotState.value == SnapshotState.Ready }

        val pendingOrder = manager.orders.value.single()
        val result = manager.collectCash(pendingOrder, "pickup_pin", "1234")
        assertTrue(result is CollectCashResult.Error)
        assertEquals("Error de conexión. Intente nuevamente.", (result as CollectCashResult.Error).message)

        // Order remains pending
        assertEquals("pending", manager.orders.value.single().paymentStatus)
    }

    @Test fun transitionOrderDoesNothingWhenOrderIsCashPending() = runBlocking {
        var transitionCalled = false
        val api = apiWithCollectCash(
            list = { Response.success(TenantOrdersListResponse(listOf(cashOrder("ord-6", "pending", "1234")))) },
            transition = { _, _, _, _ ->
                transitionCalled = true
                Response.success(cashOrder("ord-6", "pending", "1234"))
            }
        )
        val manager = OrderManager("tenant", api, stream())
        manager.refreshOrders()
        eventually { manager.snapshotState.value == SnapshotState.Ready }

        val pendingOrder = manager.orders.value.single()
        assertTrue(pendingOrder.isCashPending)

        manager.transitionOrder(pendingOrder)
        assertFalse(transitionCalled)
    }

    @Test fun voidUnpaidOrderTransitionsToCancelledAndRemovesFromActiveList() = runBlocking {
        val capturedRequest = AtomicReference<TransitionOrderRequest>()
        val api = apiWithCollectCash(
            list = { Response.success(TenantOrdersListResponse(listOf(cashOrder("ord-v1", "pending", "1234")))) },
            transition = { _, orderId, _, body ->
                capturedRequest.set(body)
                Response.success(cashOrder(orderId, "pending", "1234").copy(fulfillmentStatus = "cancelled", version = 2))
            }
        )
        val manager = OrderManager("tenant", api, stream())
        manager.refreshOrders()
        eventually { manager.snapshotState.value == SnapshotState.Ready }

        val pendingOrder = manager.orders.value.single()
        manager.voidUnpaidOrder(pendingOrder, "unpaid_cash_voided_by_pos")
        eventually { manager.orders.value.isEmpty() }
        assertEquals("cancelled", capturedRequest.get().fulfillmentStatus)
        assertEquals("unpaid_cash_voided_by_pos", capturedRequest.get().reason)
    }

    @Test fun voidUnpaidOrderOnAlreadyCancelledRemovesLocallyWithoutApiError() = runBlocking {
        var transitionCalled = false
        val api = apiWithCollectCash(
            list = { Response.success(TenantOrdersListResponse(listOf(cashOrder("ord-v2", "pending", "1234")))) },
            transition = { _, _, _, _ ->
                transitionCalled = true
                Response.success(cashOrder("ord-v2", "pending", "1234").copy(fulfillmentStatus = "cancelled"))
            }
        )
        val manager = OrderManager("tenant", api, stream())
        manager.refreshOrders()
        eventually { manager.snapshotState.value == SnapshotState.Ready }

        val cancelledOrder = manager.orders.value.single().copy(status = "cancelled")
        manager.voidUnpaidOrder(cancelledOrder, "unpaid_cash_voided_by_pos")
        eventually { manager.orders.value.isEmpty() }
        assertFalse(transitionCalled)
    }

    @Test fun voidUnpaidOrderIgnoresPaidOrders() = runBlocking {
        var transitionCalled = false
        val api = apiWithCollectCash(
            list = { Response.success(TenantOrdersListResponse(listOf(cashOrder("ord-v3", "paid", "1234")))) },
            transition = { _, _, _, _ ->
                transitionCalled = true
                Response.success(cashOrder("ord-v3", "paid", "1234"))
            }
        )
        val manager = OrderManager("tenant", api, stream())
        manager.refreshOrders()
        eventually { manager.snapshotState.value == SnapshotState.Ready }

        val paidOrder = manager.orders.value.single()
        assertEquals("paid", paidOrder.paymentStatus)
        manager.voidUnpaidOrder(paidOrder, "unpaid_cash_voided_by_pos")
        delay(100)
        assertFalse(transitionCalled)
        assertEquals(1, manager.orders.value.size)
    }

    @Test fun liveSseEventForCashPendingOrderDoesNotAutoPrint() = runBlocking {
        val printed = CopyOnWriteArrayList<String>()
        val stream = stream()
        val api = apiWithCollectCash(
            list = { Response.success(TenantOrdersListResponse(emptyList())) },
            detail = { id -> Response.success(cashOrder(id, "pending", "1234")) }
        )
        val manager = OrderManager("tenant", api, stream, autoPrint = { printed.add(it.id); Unit })
        manager.startListening()
        eventually { manager.snapshotState.value == SnapshotState.Ready }

        stream.applyEvent(TenantOrderEvent("cash-ord-1", "1"))
        eventually { manager.orders.value.any { it.id == "cash-ord-1" } }

        assertTrue(manager.orders.value.single().isCashPending)
        assertTrue(printed.isEmpty())
        manager.stopListening()
    }

    @Test fun refundCashSuccessMarksRefundedCancelledAndRemovesFromActiveView() = runBlocking {
        val capturedKey = AtomicReference<String>()
        val capturedRequest = AtomicReference<RefundCashRequest>()
        val api = apiWithCollectCash(
            list = { Response.success(TenantOrdersListResponse(listOf(cashOrder("ord-r1", "paid", "1234")))) },
            refundCash = { orderId, key, req ->
                capturedKey.set(key)
                capturedRequest.set(req)
                Response.success(
                    RefundCashResponse(
                        orderId = orderId,
                        paymentStatus = "refunded",
                        fulfillmentStatus = "cancelled",
                        refundedAt = "2026-10-05T15:00:00Z",
                        cashMovementId = "cmv-1"
                    )
                )
            }
        )
        val manager = OrderManager("tenant", api, stream())
        manager.refreshOrders()
        eventually { manager.snapshotState.value == SnapshotState.Ready }

        val paidOrder = manager.orders.value.single()
        assertEquals("paid", paidOrder.paymentStatus)

        val result = manager.refundCash(paidOrder, "  Sin stock  ", "  ")
        assertTrue("Expected Success, got $result", result is RefundCashResult.Success)
        val success = result as RefundCashResult.Success
        assertEquals("refunded", success.order.paymentStatus)
        assertEquals("cancelled", success.order.status)
        assertTrue(manager.orders.value.isEmpty())

        assertTrue(capturedKey.get().isNotBlank())
        assertEquals("Sin stock", capturedRequest.get().reason)
        assertEquals(null, capturedRequest.get().operatorNote)
    }

    @Test fun refundCashNoOpenShiftReturnsErrorAndPreservesPaidOrder() = runBlocking {
        val errorJson = """{"status":422,"title":"No open cash shift","code":"NO_OPEN_CASH_SHIFT","detail":"No existe un turno de caja abierto."}"""
        val api = apiWithCollectCash(
            list = { Response.success(TenantOrdersListResponse(listOf(cashOrder("ord-r2", "paid", "1234")))) },
            refundCash = { _, _, _ -> Response.error(422, errorJson.toResponseBody()) }
        )
        val manager = OrderManager("tenant", api, stream())
        manager.refreshOrders()
        eventually { manager.snapshotState.value == SnapshotState.Ready }

        val paidOrder = manager.orders.value.single()
        val result = manager.refundCash(paidOrder, "Error operativo")
        assertTrue(result is RefundCashResult.Error)
        assertEquals("No hay un turno de caja abierto.", (result as RefundCashResult.Error).message)

        val orderInManager = manager.orders.value.single()
        assertEquals("paid", orderInManager.paymentStatus)
        assertEquals("approved", orderInManager.status)
    }

    @Test fun refundCashConflictReturnsErrorAndPreservesPaidOrder() = runBlocking {
        val errorJson = """{"status":409,"title":"Conflict","code":"ORDER_CONFLICT","detail":"La orden ya fue reembolsada."}"""
        val api = apiWithCollectCash(
            list = { Response.success(TenantOrdersListResponse(listOf(cashOrder("ord-r3", "paid", "1234")))) },
            refundCash = { _, _, _ -> Response.error(409, errorJson.toResponseBody()) }
        )
        val manager = OrderManager("tenant", api, stream())
        manager.refreshOrders()
        eventually { manager.snapshotState.value == SnapshotState.Ready }

        val paidOrder = manager.orders.value.single()
        val result = manager.refundCash(paidOrder, "Reclamo del comensal")
        assertTrue(result is RefundCashResult.Error)
        assertEquals("La orden ya fue reembolsada.", (result as RefundCashResult.Error).message)

        val orderInManager = manager.orders.value.single()
        assertEquals("paid", orderInManager.paymentStatus)
        assertEquals("approved", orderInManager.status)
    }

    @Test fun refundCashNetworkErrorReturnsConnectionMessageAndPreservesPaidOrder() = runBlocking {
        val api = apiWithCollectCash(
            list = { Response.success(TenantOrdersListResponse(listOf(cashOrder("ord-r4", "paid", "1234")))) },
            refundCash = { _, _, _ -> throw java.io.IOException("Network connection timeout") }
        )
        val manager = OrderManager("tenant", api, stream())
        manager.refreshOrders()
        eventually { manager.snapshotState.value == SnapshotState.Ready }

        val paidOrder = manager.orders.value.single()
        val result = manager.refundCash(paidOrder, "Sin stock")
        assertTrue(result is RefundCashResult.Error)
        assertEquals("Error de conexión. Intente nuevamente.", (result as RefundCashResult.Error).message)

        val orderInManager = manager.orders.value.single()
        assertEquals("paid", orderInManager.paymentStatus)
        assertEquals("approved", orderInManager.status)
    }
}
