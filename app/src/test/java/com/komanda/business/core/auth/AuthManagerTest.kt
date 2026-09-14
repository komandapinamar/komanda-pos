package com.komanda.business.core.auth

import com.komanda.business.core.network.BarcodeLookupResponseDto
import com.komanda.business.core.network.CashShiftDto
import com.komanda.business.core.network.CashShiftResponse
import com.komanda.business.core.network.CatalogCategoryDto
import com.komanda.business.core.network.CatalogItemDto
import com.komanda.business.core.network.CatalogResponse
import com.komanda.business.core.network.CloseCashShiftRequest
import com.komanda.business.core.network.CreateCatalogItemRequest
import com.komanda.business.core.network.CreateDirectOrderRequest
import com.komanda.business.core.network.CreateInvoiceRequest
import com.komanda.business.core.network.InvoiceResponse
import com.komanda.business.core.network.KomandaApi
import com.komanda.business.core.network.OpenCashShiftRequest
import com.komanda.business.core.network.MobileLocationDto
import com.komanda.business.core.network.MobileLoginRequest
import com.komanda.business.core.network.MobileLoginResponse
import com.komanda.business.core.network.MobileTenantDto
import com.komanda.business.core.network.MobileContextResponse
import com.komanda.business.core.network.TenantOrderResponse
import com.komanda.business.core.network.TenantOrdersListResponse
import com.komanda.business.core.network.TransitionOrderRequest
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class FakeSessionStorage : SessionStorage {
    var storedSession: AuthSession? = null
    var clearCount: Int = 0

    override suspend fun saveSession(session: AuthSession) {
        storedSession = session
    }

    override suspend fun getSession(): AuthSession? = storedSession

    override suspend fun clearSession() {
        storedSession = null
        clearCount++
    }
}

class FakeKomandaApi : KomandaApi {
    var loginResult: Response<MobileLoginResponse> = Response.success(
        MobileLoginResponse("token_123", "2026-09-04T12:00:00Z")
    )
    var contextResult: Response<MobileContextResponse> = Response.success(
        MobileContextResponse(emptyList())
    )

    override suspend fun mobileLogin(body: MobileLoginRequest): Response<MobileLoginResponse> = loginResult

    override suspend fun mobileRevokeSession(authHeader: String?): Response<Unit> = Response.success(Unit)

    override suspend fun getMobileContext(authHeader: String?): Response<MobileContextResponse> = contextResult

    override suspend fun listOrders(tenantId: String, status: String?, cursor: String?): Response<TenantOrdersListResponse> =
        Response.success(TenantOrdersListResponse())

    override suspend fun getOrder(tenantId: String, orderId: String): Response<TenantOrderResponse> =
        Response.error(404, "Not found".toResponseBody("application/json".toMediaType()))

    override suspend fun transitionOrderStatus(
        tenantId: String,
        orderId: String,
        version: String,
        body: TransitionOrderRequest
    ): Response<TenantOrderResponse> =
        Response.error(404, "Not found".toResponseBody("application/json".toMediaType()))

    override suspend fun createDirectOrder(
        tenantId: String,
        idempotencyKey: String,
        body: CreateDirectOrderRequest
    ): Response<TenantOrderResponse> =
        Response.error(404, "Not found".toResponseBody("application/json".toMediaType()))

    override suspend fun listCategories(tenantId: String): Response<CatalogResponse<CatalogCategoryDto>> =
        Response.success(CatalogResponse(emptyList()))

    override suspend fun listItems(tenantId: String): Response<CatalogResponse<CatalogItemDto>> =
        Response.success(CatalogResponse(emptyList()))

    override suspend fun lookupBarcode(
        tenantId: String,
        barcode: String
    ): Response<BarcodeLookupResponseDto> =
        Response.success(BarcodeLookupResponseDto(source = "none"))

    override suspend fun createCatalogItem(
        tenantId: String,
        body: CreateCatalogItemRequest
    ): Response<CatalogItemDto> =
        Response.error(500, "Unused".toResponseBody("application/json".toMediaType()))

    override suspend fun createInvoice(
        tenantId: String,
        orderId: String,
        body: CreateInvoiceRequest
    ): Response<InvoiceResponse> =
        Response.error(404, "Not found".toResponseBody("application/json".toMediaType()))

    override suspend fun getCurrentCashShift(tenantId: String): Response<CashShiftResponse> =
        Response.success(CashShiftResponse(null))

    override suspend fun openCashShift(
        tenantId: String,
        body: OpenCashShiftRequest
    ): Response<CashShiftDto> =
        Response.error(500, "Unused".toResponseBody("application/json".toMediaType()))

    override suspend fun closeCashShift(
        tenantId: String,
        shiftId: String,
        body: CloseCashShiftRequest
    ): Response<CashShiftDto> =
        Response.error(500, "Unused".toResponseBody("application/json".toMediaType()))
}

class AuthManagerTest {

    private val fixedTime = 1788500000000L // arbitrary fixed timestamp

    @Test
    fun `happy path login with single active tenant automatically selects tenant and primary location`() = runTest {
        val fakeApi = FakeKomandaApi().apply {
            loginResult = Response.success(
                MobileLoginResponse("token_abc", "2026-09-04T12:00:00Z")
            )
            contextResult = Response.success(
                MobileContextResponse(
                    tenants = listOf(
                        MobileTenantDto(
                            id = "ten_1",
                            name = "Pizzeria Napolitana",
                            slug = "pizzeria-napolitana",
                            status = "active",
                            role = "admin",
                            primaryLocation = MobileLocationDto(
                                id = "loc_1",
                                name = "Sucursal Centro",
                                timezone = "America/Argentina/Buenos_Aires",
                                status = "active"
                            )
                        )
                    )
                )
            )
        }
        val sessionStorage = FakeSessionStorage()
        val authManager = AuthManager(fakeApi, sessionStorage, currentTimeProvider = { fixedTime })

        val success = authManager.login("admin@pizzeria.com", "validpassword")
        assertTrue(success)

        val state = authManager.authState.value
        assertTrue("Expected Authenticated state, got $state", state is AuthState.Authenticated)
        val authenticated = state as AuthState.Authenticated
        assertEquals("ten_1", authenticated.session.tenantId)
        assertEquals("Pizzeria Napolitana", authenticated.session.tenantName)
        assertEquals("loc_1", authenticated.session.locationId)
        assertEquals("Sucursal Centro", authenticated.session.locationName)
        assertEquals("token_abc", authenticated.session.token)

        assertNotNull(sessionStorage.storedSession)
        assertEquals("ten_1", sessionStorage.storedSession?.tenantId)
    }

    @Test
    fun `login with multiple active tenants prompts explicit selection`() = runTest {
        val fakeApi = FakeKomandaApi().apply {
            loginResult = Response.success(
                MobileLoginResponse("token_multi", "2026-09-04T12:00:00Z")
            )
            contextResult = Response.success(
                MobileContextResponse(
                    tenants = listOf(
                        MobileTenantDto(
                            id = "ten_1",
                            name = "Local Norte",
                            slug = "local-norte",
                            status = "active",
                            role = "admin"
                        ),
                        MobileTenantDto(
                            id = "ten_2",
                            name = "Local Sur",
                            slug = "local-sur",
                            status = "active",
                            role = "employee"
                        )
                    )
                )
            )
        }
        val sessionStorage = FakeSessionStorage()
        val authManager = AuthManager(fakeApi, sessionStorage, currentTimeProvider = { fixedTime })

        val success = authManager.login("manager@chain.com", "secretpass")
        assertTrue(success)

        val state = authManager.authState.value
        assertTrue("Expected SelectTenant state, got $state", state is AuthState.SelectTenant)
        val selectTenant = state as AuthState.SelectTenant
        assertEquals(2, selectTenant.tenants.size)
        assertNull(sessionStorage.storedSession)
    }

    @Test
    fun `selecting tenant from multiple authorized tenants transitions to Authenticated`() = runTest {
        val fakeApi = FakeKomandaApi()
        val sessionStorage = FakeSessionStorage()
        val authManager = AuthManager(fakeApi, sessionStorage, currentTimeProvider = { fixedTime })

        val availableTenants = listOf(
            MobileTenantDto(
                id = "ten_1",
                name = "Local Norte",
                slug = "local-norte",
                status = "active",
                role = "admin",
                primaryLocation = MobileLocationDto("loc_norte", "Norte 1", "UTC")
            ),
            MobileTenantDto(
                id = "ten_2",
                name = "Local Sur",
                slug = "local-sur",
                status = "active",
                role = "employee",
                primaryLocation = MobileLocationDto("loc_sur", "Sur 1", "UTC")
            )
        )

        val success = authManager.selectTenant("token_xyz", "2026-09-04T12:00:00Z", "ten_2", availableTenants)
        assertTrue(success)

        val state = authManager.authState.value
        assertTrue(state is AuthState.Authenticated)
        val auth = state as AuthState.Authenticated
        assertEquals("ten_2", auth.session.tenantId)
        assertEquals("Local Sur", auth.session.tenantName)
        assertEquals("loc_sur", auth.session.locationId)
        assertEquals("token_xyz", auth.session.token)
        assertEquals("ten_2", sessionStorage.storedSession?.tenantId)
    }

    @Test
    fun `selecting tenant NOT in authorized list is rejected`() = runTest {
        val fakeApi = FakeKomandaApi()
        val sessionStorage = FakeSessionStorage()
        val authManager = AuthManager(fakeApi, sessionStorage, currentTimeProvider = { fixedTime })

        val availableTenants = listOf(
            MobileTenantDto(
                id = "ten_1",
                name = "Local Norte",
                slug = "local-norte",
                status = "active",
                role = "admin"
            )
        )

        val success = authManager.selectTenant("token_xyz", "2026-09-04T12:00:00Z", "alien_tenant", availableTenants)
        assertFalse(success)

        val state = authManager.authState.value
        assertTrue(state is AuthState.Error)
        assertNull(sessionStorage.storedSession)
    }

    @Test
    fun `invalid credentials produces generic error without leaking credential failure type`() = runTest {
        val fakeApi = FakeKomandaApi().apply {
            loginResult = Response.error(401, "{\"code\":\"INVALID_CREDENTIALS\"}".toResponseBody("application/json".toMediaType()))
        }
        val sessionStorage = FakeSessionStorage()
        val authManager = AuthManager(fakeApi, sessionStorage, currentTimeProvider = { fixedTime })

        val success = authManager.login("bad@user.com", "wrongpassword")
        assertFalse(success)

        val state = authManager.authState.value
        assertTrue(state is AuthState.Error)
        val err = (state as AuthState.Error).message
        assertTrue(err.contains("Credenciales inválidas"))
        assertNull(sessionStorage.storedSession)
    }

    @Test
    fun `user with no active tenant membership cannot enter POS and displays access unavailable`() = runTest {
        val fakeApi = FakeKomandaApi().apply {
            loginResult = Response.success(MobileLoginResponse("token_no_tenants", "2026-09-04T12:00:00Z"))
            contextResult = Response.success(MobileContextResponse(emptyList()))
        }
        val sessionStorage = FakeSessionStorage()
        val authManager = AuthManager(fakeApi, sessionStorage, currentTimeProvider = { fixedTime })

        val success = authManager.login("user@notenants.com", "password")
        assertTrue(success)

        val state = authManager.authState.value
        assertTrue("Expected NoActiveTenant state, got $state", state is AuthState.NoActiveTenant)
        assertNull(sessionStorage.storedSession)
    }

    @Test
    fun `initialize with valid persisted session restores Authenticated state`() = runTest {
        val fakeApi = FakeKomandaApi().apply {
            contextResult = Response.success(
                MobileContextResponse(
                    tenants = listOf(
                        MobileTenantDto(
                            id = "ten_saved",
                            name = "Saved Tenant",
                            slug = "saved-tenant",
                            status = "active",
                            role = "owner",
                            primaryLocation = MobileLocationDto("loc_saved", "Loc", "UTC")
                        )
                    )
                )
            )
        }
        val sessionStorage = FakeSessionStorage().apply {
            storedSession = AuthSession(
                token = "persisted_token",
                expiresAt = "2026-09-04T12:00:00Z",
                tenantId = "ten_saved",
                tenantName = "Saved Tenant",
                locationId = "loc_saved",
                locationName = "Loc"
            )
        }
        // Current time is before expiration (2026-09-03)
        val testTime = 1788400000000L
        val authManager = AuthManager(fakeApi, sessionStorage, currentTimeProvider = { testTime })

        authManager.initialize()

        val state = authManager.authState.value
        assertTrue("Expected Authenticated, got $state", state is AuthState.Authenticated)
        assertEquals("ten_saved", (state as AuthState.Authenticated).session.tenantId)
    }

    @Test
    fun `initialize with expired session clears storage and returns to login without cached data`() = runTest {
        val fakeApi = FakeKomandaApi()
        val sessionStorage = FakeSessionStorage().apply {
            storedSession = AuthSession(
                token = "expired_token",
                expiresAt = "2026-09-01T12:00:00Z", // Past date
                tenantId = "ten_1",
                tenantName = "Expired Tenant",
                locationId = "loc_1",
                locationName = "Loc"
            )
        }
        val testTime = 1788500000000L // 2026-09-03
        val authManager = AuthManager(fakeApi, sessionStorage, currentTimeProvider = { testTime })

        authManager.initialize()

        val state = authManager.authState.value
        assertEquals(AuthState.LoggedOut, state)
        assertNull(sessionStorage.storedSession)
        assertEquals(1, sessionStorage.clearCount)
    }

    @Test
    fun `initialize with revoked session on server clears storage and returns to login`() = runTest {
        val fakeApi = FakeKomandaApi().apply {
            contextResult = Response.error(401, "{\"code\":\"INVALID_SESSION\"}".toResponseBody("application/json".toMediaType()))
        }
        val sessionStorage = FakeSessionStorage().apply {
            storedSession = AuthSession(
                token = "revoked_token",
                expiresAt = "2026-09-05T12:00:00Z", // Future date
                tenantId = "ten_1",
                tenantName = "Tenant 1",
                locationId = "loc_1",
                locationName = "Loc"
            )
        }
        val testTime = 1788400000000L
        val authManager = AuthManager(fakeApi, sessionStorage, currentTimeProvider = { testTime })

        authManager.initialize()

        val state = authManager.authState.value
        assertEquals(AuthState.LoggedOut, state)
        assertNull(sessionStorage.storedSession)
        assertEquals(1, sessionStorage.clearCount)
    }

    @Test
    fun `logout clears storage and returns to LoggedOut`() = runTest {
        val fakeApi = FakeKomandaApi()
        val sessionStorage = FakeSessionStorage().apply {
            storedSession = AuthSession(
                token = "tok",
                expiresAt = "2026-09-04T12:00:00Z",
                tenantId = "ten",
                tenantName = "Ten",
                locationId = "loc",
                locationName = "Loc"
            )
        }
        val authManager = AuthManager(fakeApi, sessionStorage)

        authManager.logout()

        assertEquals(AuthState.LoggedOut, authManager.authState.value)
        assertNull(sessionStorage.storedSession)
    }

    @Test
    fun `initialize with transient network IO error does not wipe saved credentials`() = runTest {
        val fakeApi = object : KomandaApi by FakeKomandaApi() {
            override suspend fun getMobileContext(authHeader: String?): Response<MobileContextResponse> {
                throw java.io.IOException("Socket timeout / offline")
            }
        }
        val sessionStorage = FakeSessionStorage().apply {
            storedSession = AuthSession(
                token = "persisted_token",
                expiresAt = "2026-09-05T12:00:00Z",
                tenantId = "ten_saved",
                tenantName = "Saved",
                locationId = "loc_saved",
                locationName = "Loc"
            )
        }
        val testTime = 1788400000000L
        val authManager = AuthManager(fakeApi, sessionStorage, currentTimeProvider = { testTime })

        authManager.initialize()

        // Session must be preserved
        assertNotNull(sessionStorage.storedSession)
        assertEquals(0, sessionStorage.clearCount)
        assertTrue(authManager.authState.value is AuthState.Error)
    }

    @Test
    fun `selectTenant with expired session clears session and returns false`() = runTest {
        val fakeApi = FakeKomandaApi()
        val sessionStorage = FakeSessionStorage()
        val testTime = 1788500000000L // 2026-09-03
        val authManager = AuthManager(fakeApi, sessionStorage, currentTimeProvider = { testTime })

        val availableTenants = listOf(
            MobileTenantDto("ten_1", "Local 1", "local-1", "active", "admin")
        )

        val success = authManager.selectTenant("tok", "2026-09-01T12:00:00Z", "ten_1", availableTenants)
        assertFalse(success)
        assertEquals(AuthState.LoggedOut, authManager.authState.value)
    }
}
