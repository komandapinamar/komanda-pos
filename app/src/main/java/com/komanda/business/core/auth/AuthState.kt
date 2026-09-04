package com.komanda.business.core.auth

import com.komanda.business.core.network.MobileTenantDto

sealed interface AuthState {
    data object Initializing : AuthState
    data object LoggedOut : AuthState
    data object Loading : AuthState
    data class Error(val message: String) : AuthState
    data class NoActiveTenant(val token: String) : AuthState
    data class SelectTenant(
        val token: String,
        val expiresAt: String,
        val tenants: List<MobileTenantDto>
    ) : AuthState
    data class Authenticated(
        val session: AuthSession,
        val activeTenants: List<MobileTenantDto>
    ) : AuthState
}
