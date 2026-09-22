package com.komanda.business.core.network

import android.content.Context
import android.content.SharedPreferences

interface SseCursorStorage {
    fun getCursor(tenantId: String): String?
    fun saveCursor(tenantId: String, cursor: String)
    fun clearCursor(tenantId: String)
}

class SharedPreferencesSseCursorStorage(
    context: Context,
    prefsName: String = "komanda_sse_cursor"
) : SseCursorStorage {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    override fun getCursor(tenantId: String): String? {
        return prefs.getString("cursor_$tenantId", null)
    }

    override fun saveCursor(tenantId: String, cursor: String) {
        prefs.edit().putString("cursor_$tenantId", cursor).apply()
    }

    override fun clearCursor(tenantId: String) {
        prefs.edit().remove("cursor_$tenantId").apply()
    }
}
