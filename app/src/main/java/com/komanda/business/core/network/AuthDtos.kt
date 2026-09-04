package com.komanda.business.core.network

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MobileLoginRequest(
    val email: String,
    val password: String
)

@JsonClass(generateAdapter = true)
data class MobileLoginResponse(
    val token: String,
    val expiresAt: String
)

@JsonClass(generateAdapter = true)
data class MobileLocationDto(
    val id: String,
    val name: String,
    val timezone: String,
    val status: String = "active"
)

@JsonClass(generateAdapter = true)
data class MobileTenantDto(
    val id: String,
    val name: String,
    val slug: String,
    val status: String,
    val role: String,
    val primaryLocation: MobileLocationDto? = null
)

@JsonClass(generateAdapter = true)
data class MobileContextResponse(
    val tenants: List<MobileTenantDto> = emptyList(),
    val data: List<MobileTenantDto>? = null
) {
    val activeTenants: List<MobileTenantDto>
        get() = tenants.ifEmpty { data ?: emptyList() }
}
