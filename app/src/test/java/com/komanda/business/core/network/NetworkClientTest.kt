package com.komanda.business.core.network

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkClientTest {

    @Test
    fun `auth interceptor injects bearer token from dynamic provider`() {
        var capturedToken: String? = "dynamic-session-token"
        val interceptor = NetworkClient.createAuthInterceptor { capturedToken }

        val request = Request.Builder()
            .url("https://api.komanda.test/api/v1/tenants/ten_1/orders")
            .build()

        var executedRequest: Request? = null
        val chain = object : Interceptor.Chain {
            override fun request(): Request = request
            override fun proceed(request: Request): Response {
                executedRequest = request
                return Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            override fun call() = throw UnsupportedOperationException()
            override fun connection() = null
            override fun connectTimeoutMillis() = 0
            override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            override fun readTimeoutMillis() = 0
            override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            override fun writeTimeoutMillis() = 0
            override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        }

        interceptor.intercept(chain)

        assertNotNull(executedRequest)
        assertEquals("Bearer dynamic-session-token", executedRequest?.header("Authorization"))
        assertEquals("application/json", executedRequest?.header("Accept"))
    }

    @Test
    fun `auth interceptor preserves explicit Authorization header`() {
        val interceptor = NetworkClient.createAuthInterceptor { "dynamic-token" }

        val request = Request.Builder()
            .url("https://api.komanda.test/api/v1/auth/mobile/context")
            .header("Authorization", "Bearer custom-token")
            .build()

        var executedRequest: Request? = null
        val chain = object : Interceptor.Chain {
            override fun request(): Request = request
            override fun proceed(request: Request): Response {
                executedRequest = request
                return Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            override fun call() = throw UnsupportedOperationException()
            override fun connection() = null
            override fun connectTimeoutMillis() = 0
            override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            override fun readTimeoutMillis() = 0
            override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            override fun writeTimeoutMillis() = 0
            override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        }

        interceptor.intercept(chain)

        assertNotNull(executedRequest)
        assertEquals("Bearer custom-token", executedRequest?.header("Authorization"))
    }

    @Test
    fun `auth interceptor does not add Authorization when token provider returns null`() {
        val interceptor = NetworkClient.createAuthInterceptor { null }

        val request = Request.Builder()
            .url("https://api.komanda.test/api/v1/auth/mobile/sessions")
            .build()

        var executedRequest: Request? = null
        val chain = object : Interceptor.Chain {
            override fun request(): Request = request
            override fun proceed(request: Request): Response {
                executedRequest = request
                return Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            override fun call() = throw UnsupportedOperationException()
            override fun connection() = null
            override fun connectTimeoutMillis() = 0
            override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            override fun readTimeoutMillis() = 0
            override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            override fun writeTimeoutMillis() = 0
            override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        }

        interceptor.intercept(chain)

        assertNotNull(executedRequest)
        assertNull(executedRequest?.header("Authorization"))
    }

    @Test
    fun `logging interceptor prevents credential-bearing body logs`() {
        val logging = NetworkClient.createLoggingInterceptor()
        // Ensure level is not Level.BODY to prevent leaking passwords and tokens in HTTP bodies
        assertNotEquals(HttpLoggingInterceptor.Level.BODY, logging.level)
        assertEquals(HttpLoggingInterceptor.Level.BASIC, logging.level)
    }

    private fun assertNotNull(value: Any?) {
        assertTrue(value != null)
    }
}
