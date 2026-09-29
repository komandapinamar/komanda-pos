package com.komanda.business.core.network

import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.random.Random

enum class ConnectionState { CONNECTING, LIVE, RECONNECTING, UNAUTHORIZED }

class SseOrderEventListener(
    private val baseUrl: String,
    private val tenantId: String,
    private val authToken: String? = null,
    moshi: Moshi = Moshi.Builder().build(),
    private val cursorStorage: SseCursorStorage? = null,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS).retryOnConnectionFailure(true).build()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val adapter = moshi.adapter(TenantOrderEvent::class.java)
    private val resetAdapter = moshi.adapter(TenantOrderResetEvent::class.java)
    private val _connectionState = MutableStateFlow(ConnectionState.CONNECTING)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // Called on the stream's ordered callback thread. A failed apply closes the stream
    // before its successor can be delivered or acknowledged.
    var applyEvent: suspend (TenantOrderEvent) -> Boolean = { false }
    var reconcile: suspend () -> Boolean = { false }

    private val lock = Any()
    private var generation = 0
    private var running = false
    private var source: EventSource? = null
    private var retry: Job? = null
    private var reconciliation: Job? = null
    private var attempts = 0
    private var cursor = cursorStorage?.getCursor(tenantId)

    fun start() {
        synchronized(lock) {
            if (running) return
            running = true
            attempts = 0
            _connectionState.value = ConnectionState.CONNECTING
            connectOrRecover()
        }
    }

    // Core parses Last-Event-ID inside an already-open SSE stream. A malformed
    // persisted cursor therefore looks like a successful HTTP 200 followed by a
    // close. Validate it before sending any request, but retain it until a full
    // authoritative snapshot succeeds.
    private fun validCursor(value: String): Boolean =
        value.isNotEmpty() && value.all { it in '0'..'9' } && value.toLongOrNull() != null

    private fun connectOrRecover() {
        if (cursor?.let { !validCursor(it) } == true) {
            beginRecovery(++generation)
        } else {
            connect()
        }
    }

    private fun beginRecovery(epoch: Int) {
        if (!isCurrent(epoch)) return
        generation++ // reject callbacks from the rejected stream
        source?.cancel()
        source = null
        _connectionState.value = ConnectionState.RECONNECTING
        val recoveryEpoch = generation
        scope.launch {
            val restored = try { reconcile() } catch (e: CancellationException) {
                return@launch
            } catch (_: Exception) { false }
            if (restored) synchronized(lock) {
                if (isCurrent(recoveryEpoch)) {
                    try {
                        cursorStorage?.clearCursor(tenantId)
                        cursor = null
                    } catch (_: Exception) { /* retain the cursor and try again */ }
                }
            }
            scheduleRetry(recoveryEpoch)
        }
    }

    private fun connect() {
        val epoch = ++generation
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/v1/tenants/$tenantId/orders/events")
            .addHeader("Accept", "text/event-stream")
            .apply {
                authToken?.takeIf { it.isNotBlank() }?.let { addHeader("Authorization", "Bearer $it") }
                cursor?.let { addHeader("Last-Event-ID", it) }
            }.build()
        source = EventSources.createFactory(client).newEventSource(request, object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                if (!isCurrent(epoch)) return
                _connectionState.value = ConnectionState.LIVE
                // Snapshot after subscription: replay and snapshot can overlap, but the
                // manager serializes updates and never auto-prints from reconciliation.
                reconciliation = scope.launch { if (isCurrent(epoch)) reconcile() }
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (!isCurrent(epoch)) return
                if (type == "reset") {
                    handleReset(epoch, data)
                    return
                }
                if (type != "order" && type != null) return
                val event = try { adapter.fromJson(data) } catch (_: Exception) { null }
                val sequence = id?.takeIf { it.isNotBlank() } ?: event?.sequence
                if (event == null || event.orderId.isBlank() || sequence.isNullOrBlank() ||
                    !validCursor(sequence) ||
                    event.sequence != sequence) {
                    scheduleRetry(epoch)
                    return
                }
                val applied = try {
                    runBlocking { applyEvent(event) }
                } catch (e: CancellationException) { false } catch (_: Exception) { false }
                if (!applied || !isCurrent(epoch)) {
                    scheduleRetry(epoch)
                    return
                }
                try {
                    cursorStorage?.saveCursor(tenantId, sequence)
                    cursor = sequence
                    attempts = 0
                } catch (_: Exception) {
                    scheduleRetry(epoch)
                }
            }

            override fun onClosed(eventSource: EventSource) = scheduleRetry(epoch)

            // Server declared our cursor unusable (malformed/ahead) and will stream
            // forward from head. Reconcile an authoritative snapshot before trusting
            // subsequent events, then persist head as the new cursor.
            private fun handleReset(epoch: Int, data: String) {
                val head = try {
                    resetAdapter.fromJson(data)?.headSequence
                } catch (_: Exception) { null }
                val restored = try {
                    runBlocking { reconcile() }
                } catch (e: CancellationException) { false } catch (_: Exception) { false }
                if (!restored || !isCurrent(epoch)) {
                    scheduleRetry(epoch)
                    return
                }
                synchronized(lock) {
                    if (!isCurrent(epoch)) return
                    try {
                        if (head != null && validCursor(head)) {
                            cursorStorage?.saveCursor(tenantId, head)
                            cursor = head
                        } else {
                            cursorStorage?.clearCursor(tenantId)
                            cursor = null
                        }
                        attempts = 0
                    } catch (_: Exception) {
                        scheduleRetry(epoch)
                    }
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                if (!isCurrent(epoch)) return
                if (response?.code == 401 || response?.code == 403) {
                    synchronized(lock) {
                        if (isCurrent(epoch)) {
                            source?.cancel()
                            source = null
                            running = false
                            generation++
                            _connectionState.value = ConnectionState.UNAUTHORIZED
                        }
                    }
                    return
                }
                if ((response?.code == 400 || response?.code == 410 || response?.code == 422) && cursor != null) {
                    synchronized(lock) { beginRecovery(epoch) }
                } else scheduleRetry(epoch)
            }
        })
    }

    private fun isCurrent(epoch: Int) = synchronized(lock) { running && generation == epoch }

    private fun scheduleRetry(epoch: Int) {
        synchronized(lock) {
            if (!isCurrent(epoch)) return
            generation++ // invalidate late callbacks from this connection
            source?.cancel()
            source = null
            _connectionState.value = ConnectionState.RECONNECTING
            retry?.cancel()
            reconciliation?.cancel()
            reconciliation = null
            val delayMs = min(30_000L, 1_000L shl min(attempts++, 5))
            retry = scope.launch {
                delay(delayMs + Random.nextLong(0, 500))
                synchronized(lock) { if (running && generation == epoch + 1) connectOrRecover() }
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            running = false
            generation++
            retry?.cancel()
            retry = null
            reconciliation?.cancel()
            reconciliation = null
            source?.cancel()
            source = null
            _connectionState.value = ConnectionState.CONNECTING
        }
    }
}
