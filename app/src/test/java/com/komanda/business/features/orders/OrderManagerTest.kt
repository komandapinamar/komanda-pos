package com.komanda.business.features.orders

import com.komanda.business.core.network.KomandaApi
import com.komanda.business.core.network.SseOrderEventListener
import com.komanda.business.core.network.TenantOrderEvent
import com.komanda.business.core.network.TenantOrderResponse
import com.komanda.business.core.network.TenantOrdersListResponse
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
}
