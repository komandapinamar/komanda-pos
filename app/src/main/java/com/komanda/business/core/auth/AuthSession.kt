package com.komanda.business.core.auth

data class AuthSession(
    val token: String,
    val expiresAt: String,
    val tenantId: String,
    val tenantName: String,
    val locationId: String,
    val locationName: String
)
