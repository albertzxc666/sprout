package com.transcard.data.preferences

import platform.Foundation.NSUserDefaults

actual class Preferences {
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults

    actual fun getBoolean(key: String, default: Boolean): Boolean {
        return if (defaults.objectForKey(key) == null) default
        else defaults.boolForKey(key)
    }

    actual fun setBoolean(key: String, value: Boolean) {
        defaults.setBool(value, forKey = key)
    }
}
