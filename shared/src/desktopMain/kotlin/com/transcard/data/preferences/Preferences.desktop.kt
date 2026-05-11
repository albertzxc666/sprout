package com.transcard.data.preferences

import java.util.prefs.Preferences as JavaPreferences

actual class Preferences {
    private val node: JavaPreferences = JavaPreferences.userRoot().node("com/transcard")

    actual fun getBoolean(key: String, default: Boolean): Boolean =
        node.getBoolean(key, default)

    actual fun setBoolean(key: String, value: Boolean) {
        node.putBoolean(key, value)
        node.flush()
    }
}
