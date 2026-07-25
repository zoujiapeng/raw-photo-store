package com.zoujiapeng.rawjudge.data

import android.content.Context

class SessionStore(context: Context) {
    private val preferences = context.getSharedPreferences("rawjudge-session", Context.MODE_PRIVATE)

    var token: String?
        get() = preferences.getString("token", null)
        set(value) {
            preferences.edit().apply {
                if (value == null) remove("token") else putString("token", value)
            }.apply()
        }

    fun clear() {
        preferences.edit().clear().apply()
    }
}
