package com.komanda.business.core.auth

interface SessionStorage {
    suspend fun saveSession(session: AuthSession)
    suspend fun getSession(): AuthSession?
    suspend fun clearSession()
}
