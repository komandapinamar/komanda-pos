package com.komanda.business.core.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

class LauncherManager(private val context: Context) {

    private val aliasComponent = ComponentName(context, "${context.packageName}.HomeLauncherAlias")

    fun isHomeLauncherEnabled(): Boolean {
        val state = context.packageManager.getComponentEnabledSetting(aliasComponent)
        return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }

    fun setHomeLauncherEnabled(enabled: Boolean) {
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        context.packageManager.setComponentEnabledSetting(
            aliasComponent,
            state,
            PackageManager.DONT_KILL_APP
        )
    }

    fun openHomeSettings() {
        try {
            val intent = Intent(Settings.ACTION_HOME_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(fallback)
        }
    }
}
