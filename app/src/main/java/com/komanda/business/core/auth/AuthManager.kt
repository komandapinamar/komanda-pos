package com.komanda.business.core.auth

import android.util.Log
import com.komanda.business.core.network.KomandaApi
import com.komanda.business.core.network.MobileLoginRequest
import com.komanda.business.core.network.MobileTenantDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class AuthManager(
    private val api: KomandaApi,
    private val sessionStorage: SessionStorage,
    private val currentTimeProvider: () -> Long = { System.currentTimeMillis() }
) {
    private val tag = "AuthManager"

    private val _authState = MutableStateFlow<AuthState>(AuthState.Initializing)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    val currentToken: String?
        get() = when (val state = _authState.value) {
            is AuthState.Authenticated -> state.session.token
            is AuthState.SelectTenant -> state.token
            is AuthState.NoActiveTenant -> state.token
            else -> null
        }

    val currentSession: AuthSession?
        get() = (_authState.value as? AuthState.Authenticated)?.session

    suspend fun initialize() {
        val session = sessionStorage.getSession()
        if (session == null) {
            _authState.value = AuthState.LoggedOut
            return
        }

        if (isExpired(session.expiresAt)) {
            Log.w(tag, "Persisted session is expired. Clearing storage.")
            sessionStorage.clearSession()
            _authState.value = AuthState.LoggedOut
            return
        }

        try {
            val response = api.getMobileContext("Bearer ${session.token}")
            if (!response.isSuccessful || response.body() == null) {
                Log.w(tag, "Persisted session invalid or revoked on server (${response.code()}). Clearing storage.")
                sessionStorage.clearSession()
                _authState.value = AuthState.LoggedOut
                return
            }

            val activeTenants = response.body()!!.activeTenants
            val currentTenant = activeTenants.find { it.id == session.tenantId }

            if (currentTenant != null) {
                _authState.value = AuthState.Authenticated(session, activeTenants)
            } else {
                when {
                    activeTenants.isEmpty() -> {
                        sessionStorage.clearSession()
                        _authState.value = AuthState.NoActiveTenant(session.token)
                    }
                    activeTenants.size == 1 -> {
                        val single = activeTenants.first()
                        val updated = AuthSession(
                            token = session.token,
                            expiresAt = session.expiresAt,
                            tenantId = single.id,
                            tenantName = single.name,
                            locationId = single.primaryLocation?.id ?: "",
                            locationName = single.primaryLocation?.name ?: ""
                        )
                        sessionStorage.saveSession(updated)
                        _authState.value = AuthState.Authenticated(updated, activeTenants)
                    }
                    else -> {
                        _authState.value = AuthState.SelectTenant(
                            token = session.token,
                            expiresAt = session.expiresAt,
                            tenants = activeTenants
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to validate session context", e)
            sessionStorage.clearSession()
            _authState.value = AuthState.LoggedOut
        }
    }

    suspend fun login(email: String, password: String): Boolean {
        _authState.value = AuthState.Loading
        try {
            val loginRes = api.mobileLogin(
                MobileLoginRequest(
                    email = email.trim(),
                    password = password
                )
            )

            if (!loginRes.isSuccessful || loginRes.body() == null) {
                _authState.value = AuthState.Error("Credenciales inválidas. Verificá tu usuario y contraseña.")
                return false
            }

            val loginData = loginRes.body()!!
            val contextRes = api.getMobileContext("Bearer ${loginData.token}")

            if (!contextRes.isSuccessful || contextRes.body() == null) {
                _authState.value = AuthState.Error("Error al obtener contexto operativo.")
                return false
            }

            val tenants = contextRes.body()!!.activeTenants

            when {
                tenants.isEmpty() -> {
                    _authState.value = AuthState.NoActiveTenant(loginData.token)
                }
                tenants.size == 1 -> {
                    // HAPPY_PATH: Automatic single-tenant selection
                    val single = tenants.first()
                    val session = AuthSession(
                        token = loginData.token,
                        expiresAt = loginData.expiresAt,
                        tenantId = single.id,
                        tenantName = single.name,
                        locationId = single.primaryLocation?.id ?: "",
                        locationName = single.primaryLocation?.name ?: ""
                    )
                    sessionStorage.saveSession(session)
                    _authState.value = AuthState.Authenticated(session, tenants)
                }
                else -> {
                    // MULTIPLE_TENANTS: Explicit selection required
                    _authState.value = AuthState.SelectTenant(
                        token = loginData.token,
                        expiresAt = loginData.expiresAt,
                        tenants = tenants
                    )
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(tag, "Login exception", e)
            _authState.value = AuthState.Error("Credenciales inválidas. Verificá tu usuario y contraseña.")
            return false
        }
    }

    suspend fun selectTenant(
        token: String,
        expiresAt: String,
        tenantId: String,
        availableTenants: List<MobileTenantDto>
    ): Boolean {
        val selected = availableTenants.find { it.id == tenantId }
        if (selected == null) {
            _authState.value = AuthState.Error("Restaurante no autorizado.")
            return false
        }

        val session = AuthSession(
            token = token,
            expiresAt = expiresAt,
            tenantId = selected.id,
            tenantName = selected.name,
            locationId = selected.primaryLocation?.id ?: "",
            locationName = selected.primaryLocation?.name ?: ""
        )
        sessionStorage.saveSession(session)
        _authState.value = AuthState.Authenticated(session, availableTenants)
        return true
    }

    suspend fun logout() {
        sessionStorage.clearSession()
        _authState.value = AuthState.LoggedOut
    }

    fun clearError() {
        if (_authState.value is AuthState.Error) {
            _authState.value = AuthState.LoggedOut
        }
    }

    private fun isExpired(expiresAt: String): Boolean {
        val expiryMillis = parseIsoTimestamp(expiresAt) ?: return true
        return expiryMillis <= currentTimeProvider()
    }

    private fun parseIsoTimestamp(isoDate: String): Long? {
        return try {
            val cleaned = isoDate.split(".")[0].replace("Z", "")
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            format.parse(cleaned)?.time
        } catch (_: Exception) {
            null
        }
    }
}
