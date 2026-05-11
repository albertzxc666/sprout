package com.transcard.data.preferences

import android.content.Context

actual class Preferences(context: Context) {
    private val prefs = context.getSharedPreferences("transcard_prefs", Context.MODE_PRIVATE)

    actual fun getBoolean(key: String, default: Boolean): Boolean =
        prefs.getBoolean(key, default)

    actual fun setBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }
}
