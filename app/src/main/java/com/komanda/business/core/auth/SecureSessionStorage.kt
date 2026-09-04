package com.komanda.business.core.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureSessionStorage(
    private val context: Context,
    private val prefsName: String = "komanda_secure_auth"
) : SessionStorage {

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                prefsName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Throwable) {
            // Fallback for JVM unit tests or test environments lacking Android KeyStore
            context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        }
    }

    override suspend fun saveSession(session: AuthSession) {
        prefs.edit()
            .putString(KEY_TOKEN, session.token)
            .putString(KEY_EXPIRES_AT, session.expiresAt)
            .putString(KEY_TENANT_ID, session.tenantId)
            .putString(KEY_TENANT_NAME, session.tenantName)
            .putString(KEY_LOCATION_ID, session.locationId)
            .putString(KEY_LOCATION_NAME, session.locationName)
            .apply()
    }

    override suspend fun getSession(): AuthSession? {
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val expiresAt = prefs.getString(KEY_EXPIRES_AT, null) ?: return null
        val tenantId = prefs.getString(KEY_TENANT_ID, null) ?: return null
        val tenantName = prefs.getString(KEY_TENANT_NAME, "") ?: ""
        val locationId = prefs.getString(KEY_LOCATION_ID, "") ?: ""
        val locationName = prefs.getString(KEY_LOCATION_NAME, "") ?: ""

        return AuthSession(
            token = token,
            expiresAt = expiresAt,
            tenantId = tenantId,
            tenantName = tenantName,
            locationId = locationId,
            locationName = locationName
        )
    }

    override suspend fun clearSession() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_EXPIRES_AT = "auth_expires_at"
        private const val KEY_TENANT_ID = "auth_tenant_id"
        private const val KEY_TENANT_NAME = "auth_tenant_name"
        private const val KEY_LOCATION_ID = "auth_location_id"
        private const val KEY_LOCATION_NAME = "auth_location_name"
    }
}
