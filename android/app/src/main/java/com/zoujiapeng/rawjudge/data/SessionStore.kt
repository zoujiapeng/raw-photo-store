package com.zoujiapeng.rawjudge.data

import android.content.Context
import com.zoujiapeng.rawjudge.BuildConfig

class SessionStore(context: Context) {
    private val preferences = context.getSharedPreferences("rawjudge-session", Context.MODE_PRIVATE)

    var token: String?
        get() = preferences.getString("token", null)
        set(value) {
            preferences.edit().apply {
                if (value == null) remove("token") else putString("token", value)
            }.apply()
        }

    var serverUrl: String
        get() = preferences.getString("server_url", BuildConfig.API_BASE_URL)
            ?.trimEnd('/')
            .orEmpty()
            .ifBlank { BuildConfig.API_BASE_URL }
        set(value) {
            preferences.edit().putString("server_url", value.trim().trimEnd('/')).apply()
        }

    fun clearToken() {
        preferences.edit().remove("token").apply()
    }
}
