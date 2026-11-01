package com.example.myapplication

import android.content.Context

/** Servidor e token do receptor no Arch, guardados em SharedPreferences. */
class BridgeConfig(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("bridge_config", Context.MODE_PRIVATE)

    var server: String
        get() = prefs.getString("server", "") ?: ""
        set(value) = prefs.edit().putString("server", value.trim()).apply()

    var token: String
        get() = prefs.getString("token", "") ?: ""
        set(value) = prefs.edit().putString("token", value.trim()).apply()

    val isConfigured: Boolean get() = server.isNotBlank() && token.isNotBlank()

    /** Aceita "192.168.0.50:8787" ou uma URL completa; devolve a URL do endpoint /notify. */
    fun endpoint(): String {
        val base = if (server.contains("://")) server else "http://$server"
        return base.trimEnd('/') + "/notify"
    }
}
