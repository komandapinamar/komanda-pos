package com.komanda.business.core.network

import android.util.Log
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

enum class ConnectionState {
    CONNECTING,
    LIVE,
    RECONNECTING
}

class SseOrderEventListener(
    private val baseUrl: String,
    private val tenantId: String,
    private val authToken: String? = null,
    private val moshi: Moshi = Moshi.Builder().build()
) {

    private val tag = "SseOrderListener"
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _connectionState = MutableStateFlow(ConnectionState.CONNECTING)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _events = MutableSharedFlow<TenantOrderEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<TenantOrderEvent> = _events.asSharedFlow()

    private var eventSource: EventSource? = null
    private var lastSequence: String? = null
    private var isRunning = false

    private val sseClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val adapter = moshi.adapter(TenantOrderEvent::class.java)

    fun start() {
        if (isRunning) return
        isRunning = true
        _connectionState.value = ConnectionState.CONNECTING
        connect()
    }

    private fun connect() {
        val urlBuilder = StringBuilder("${baseUrl.trimEnd('/')}/api/v1/tenants/$tenantId/orders/events")
        lastSequence?.let { cursor ->
            urlBuilder.append("?cursor=").append(cursor)
        }

        val requestBuilder = Request.Builder()
            .url(urlBuilder.toString())
            .addHeader("Accept", "text/event-stream")

        authToken?.let { token ->
            if (token.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $token")
            }
        }

        lastSequence?.let { seq ->
            requestBuilder.addHeader("Last-Event-ID", seq)
        }

        val request = requestBuilder.build()
        val factory = EventSources.createFactory(sseClient)

        eventSource = factory.newEventSource(request, object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                Log.i(tag, "SSE connection opened to orders stream.")
                _connectionState.value = ConnectionState.LIVE
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (id != null) {
                    lastSequence = id
                }

                _connectionState.value = ConnectionState.LIVE

                if (type == "order" || type == null) {
                    try {
                        val event = adapter.fromJson(data)
                        if (event != null && event.orderId.isNotBlank()) {
                            scope.launch {
                                _events.emit(event)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(tag, "Failed to parse SSE order event: $data", e)
                    }
                }
            }

            override fun onClosed(eventSource: EventSource) {
                Log.w(tag, "SSE connection closed. Reconnecting...")
                _connectionState.value = ConnectionState.RECONNECTING
                if (isRunning) reconnect()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                Log.e(tag, "SSE connection error: ${t?.message}. Reconnecting...", t)
                _connectionState.value = ConnectionState.RECONNECTING
                if (isRunning) reconnect()
            }
        })
    }

    private fun reconnect() {
        eventSource?.cancel()
        eventSource = null
        scope.launch {
            kotlinx.coroutines.delay(2000)
            if (isRunning) {
                connect()
            }
        }
    }

    fun stop() {
        isRunning = false
        eventSource?.cancel()
        eventSource = null
        _connectionState.value = ConnectionState.CONNECTING
        Log.i(tag, "SSE connection stopped.")
    }
}
