package com.komanda.business.core.network

import kotlinx.coroutines.CompletableDeferred
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.BufferedSource
import okio.buffer
import okio.source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class SseOrderEventListenerTest {
    private class MemoryCursor(initial: String? = null) : SseCursorStorage {
        @Volatile var value: String? = initial
        val cleared = AtomicInteger()
        override fun getCursor(tenantId: String) = value
        override fun saveCursor(tenantId: String, cursor: String) { value = cursor }
        override fun clearCursor(tenantId: String) { cleared.incrementAndGet(); value = null }
    }

    private fun response(request: Request, code: Int, body: ResponseBody = "".toResponseBody()) =
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code)
            .message("test").body(body).build()

    private fun eventually(timeoutMs: Long = 6000, predicate: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (!predicate() && System.nanoTime() < deadline) Thread.sleep(10)
        assertTrue("Condition not reached", predicate())
    }

    @Test fun malformedStoredCursorWaitsForSnapshotBeforeClearingAndRetriesWhenSnapshotFails() {
        val cursor = MemoryCursor("not-a-sequence")
        val requests = CopyOnWriteArrayList<String?>()
        val client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            requests.add(chain.request().header("Last-Event-ID"))
            response(chain.request(), 401)
        }).build()
        val listener = SseOrderEventListener("https://example.test", "tenant", cursorStorage = cursor, client = client)
        val snapshotSucceeded = AtomicReference(false)
        val snapshots = AtomicInteger()
        val firstFailed = CountDownLatch(1)
        listener.reconcile = {
            snapshots.incrementAndGet()
            val result = snapshotSucceeded.get()
            if (!result) firstFailed.countDown()
            result
        }
        listener.start()
        try {
            assertTrue(firstFailed.await(3, TimeUnit.SECONDS))
            assertEquals("not-a-sequence", cursor.value)
            assertEquals(0, cursor.cleared.get())
            assertTrue(requests.isEmpty())
            snapshotSucceeded.set(true)
            eventually { listener.connectionState.value == ConnectionState.UNAUTHORIZED }
            assertEquals(2, snapshots.get())
            assertEquals(1, cursor.cleared.get())
            assertNull(cursor.value)
            assertEquals(listOf(null), requests.toList())
        } finally { listener.stop() }
    }

    @Test fun rejectedValidCursorReconcilesBeforeClearing() {
        val cursor = MemoryCursor("42")
        val requests = CopyOnWriteArrayList<String?>()
        val client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            val previous = chain.request().header("Last-Event-ID")
            requests.add(previous)
            response(chain.request(), if (previous == null) 401 else 422)
        }).build()
        val listener = SseOrderEventListener("https://example.test", "tenant", cursorStorage = cursor, client = client)
        val snapshots = AtomicInteger()
        listener.reconcile = { snapshots.incrementAndGet(); true }
        listener.start()
        try {
            eventually { listener.connectionState.value == ConnectionState.UNAUTHORIZED }
            assertEquals(1, snapshots.get())
            assertEquals(1, cursor.cleared.get())
            assertEquals(listOf("42", null), requests.toList())
        } finally { listener.stop() }
    }

    @Test fun transientFailuresKeepValidCursorAndRetryWithBackoff() {
        val cursor = MemoryCursor("42")
        val requests = CopyOnWriteArrayList<Pair<String?, Long>>()
        val client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            requests.add(chain.request().header("Last-Event-ID") to System.nanoTime())
            response(chain.request(), 503)
        }).build()
        val listener = SseOrderEventListener("https://example.test", "tenant", cursorStorage = cursor, client = client)
        listener.start()
        try {
            eventually { requests.size >= 2 }
            assertEquals(listOf("42", "42"), requests.take(2).map { it.first })
            assertTrue(TimeUnit.NANOSECONDS.toMillis(requests[1].second - requests[0].second) >= 900)
            assertEquals(0, cursor.cleared.get())
            assertEquals("42", cursor.value)
            Thread.sleep(600)
            assertEquals(2, requests.size) // next retry uses a longer delay
        } finally { listener.stop() }
        Thread.sleep(200)
        assertEquals(2, requests.size)
    }

    @Test fun interruptedStreamReconcilesAndAppliesMissedEventAfterReconnect() {
        val cursor = MemoryCursor("11")
        val connections = AtomicInteger()
        val reconciles = AtomicInteger()
        val applied = CopyOnWriteArrayList<String>()
        val output = AtomicReference<PipedOutputStream?>()
        val frame = "id: 12\nevent: order\ndata: {\"orderId\":\"new\",\"sequence\":\"12\"}\n\n"
        val client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            val number = connections.incrementAndGet()
            assertEquals("11", chain.request().header("Last-Event-ID"))
            if (number == 1) response(chain.request(), 503)
            else {
                val writer = PipedOutputStream()
                output.set(writer)
                val input = PipedInputStream(writer)
                writer.write(frame.toByteArray())
                writer.flush()
                val body = object : ResponseBody() {
                    override fun contentType() = "text/event-stream".toMediaType()
                    override fun contentLength() = -1L
                    override fun source(): BufferedSource = input.source().buffer()
                    override fun close() { input.close(); writer.close() }
                }
                response(chain.request(), 200, body)
            }
        }).build()
        val listener = SseOrderEventListener("https://example.test", "tenant", cursorStorage = cursor, client = client)
        listener.reconcile = { reconciles.incrementAndGet(); true }
        listener.applyEvent = { applied.add(it.orderId); true }
        listener.start()
        try {
            eventually { cursor.value == "12" }
            assertTrue(connections.get() >= 2)
            assertEquals(listOf("new"), applied.toList())
            eventually { reconciles.get() >= 1 }
            assertEquals(0, cursor.cleared.get())
        } finally { listener.stop(); output.get()?.close() }
    }

    @Test fun orderedApplyDoesNotAckOrDeliverSuccessorsBeforeFailedPredecessor() {
        val cursor = MemoryCursor()
        val requests = CopyOnWriteArrayList<String?>()
        val blocker = CompletableDeferred<Unit>()
        val entered = CountDownLatch(1)
        val applied = CopyOnWriteArrayList<String>()
        val frame = (1..2).joinToString("") { n ->
            "id: $n\nevent: order\ndata: {\"orderId\":\"$n\",\"sequence\":\"$n\"}\n\n"
        }
        val client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            requests.add(chain.request().header("Last-Event-ID"))
            response(chain.request(), 200, frame.toResponseBody("text/event-stream".toMediaType()))
        }).build()
        val listener = SseOrderEventListener("https://example.test", "tenant", cursorStorage = cursor, client = client)
        val failures = AtomicInteger()
        listener.applyEvent = { event ->
            applied.add(event.sequence)
            if (event.sequence == "1" && failures.getAndIncrement() == 0) {
                entered.countDown()
                blocker.await()
                false
            } else true
        }
        listener.start()
        try {
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            assertEquals(listOf("1"), applied.toList())
            assertNull(cursor.value)
            blocker.complete(Unit)
            eventually { cursor.value == "2" }
            assertEquals(listOf(null, null), requests.take(2))
            assertEquals(listOf("1", "1", "2"), applied.take(3))
        } finally { listener.stop() }
    }

    @Test fun startingTwiceKeepsOneStreamAndStoppingPreventsLateReconnect() {
        val connections = AtomicInteger()
        val streams = CopyOnWriteArrayList<PipedOutputStream>()
        val client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            connections.incrementAndGet()
            val output = PipedOutputStream()
            streams.add(output)
            val input = PipedInputStream(output)
            val body = object : ResponseBody() {
                override fun contentType() = "text/event-stream".toMediaType()
                override fun contentLength() = -1L
                override fun source(): BufferedSource = input.source().buffer()
                override fun close() { input.close(); output.close() }
            }
            response(chain.request(), 200, body)
        }).build()
        val listener = SseOrderEventListener("https://example.test", "tenant", client = client)
        listener.start()
        listener.start()
        try {
            eventually { listener.connectionState.value == ConnectionState.LIVE }
            assertEquals(1, connections.get())
            listener.stop()
            streams.forEach { it.close() }
            Thread.sleep(1300)
            assertEquals(1, connections.get())
            listener.start()
            eventually { connections.get() == 2 && listener.connectionState.value == ConnectionState.LIVE }
            assertEquals(2, connections.get())
        } finally {
            listener.stop()
            streams.forEach { it.close() }
        }
    }

    @Test fun cursorAheadResetReconcilesSnapshotThenPersistsHeadBeforeApplyingEvents() {
        val cursor = MemoryCursor("999")
        val reconciles = AtomicInteger()
        val applied = CopyOnWriteArrayList<String>()
        val output = PipedOutputStream()
        val input = PipedInputStream(output)
        output.write(
            ("event: reset\ndata: {\"reason\":\"ahead\",\"headSequence\":\"5\"}\n\n" +
                "id: 6\nevent: order\ndata: {\"orderId\":\"n\",\"sequence\":\"6\"}\n\n").toByteArray()
        )
        output.flush()
        val body = object : ResponseBody() {
            override fun contentType() = "text/event-stream".toMediaType()
            override fun contentLength() = -1L
            override fun source(): BufferedSource = input.source().buffer()
            override fun close() { input.close(); output.close() }
        }
        val client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            assertEquals("999", chain.request().header("Last-Event-ID"))
            response(chain.request(), 200, body)
        }).build()
        val listener = SseOrderEventListener("https://example.test", "tenant", cursorStorage = cursor, client = client)
        listener.reconcile = { reconciles.incrementAndGet(); true }
        listener.applyEvent = { applied.add(it.orderId); true }
        listener.start()
        try {
            eventually { cursor.value == "6" }
            assertTrue(reconciles.get() >= 1)
            assertEquals(listOf("n"), applied.toList())
            assertEquals(0, cursor.cleared.get())
        } finally { listener.stop(); output.close() }
    }

    @Test fun failedReconcileOnResetKeepsCursorAndRetries() {
        val cursor = MemoryCursor("999")
        val requests = CopyOnWriteArrayList<String?>()
        val body = "event: reset\ndata: {\"reason\":\"ahead\",\"headSequence\":\"5\"}\n\n"
            .toResponseBody("text/event-stream".toMediaType())
        val client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            requests.add(chain.request().header("Last-Event-ID"))
            response(chain.request(), 200, body)
        }).build()
        val listener = SseOrderEventListener("https://example.test", "tenant", cursorStorage = cursor, client = client)
        listener.reconcile = { false }
        listener.start()
        try {
            eventually { requests.size >= 2 }
            assertEquals("999", cursor.value)
            assertEquals(0, cursor.cleared.get())
        } finally { listener.stop() }
    }
}
