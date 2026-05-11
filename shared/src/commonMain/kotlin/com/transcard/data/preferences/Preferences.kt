package com.transcard.data.preferences

expect class Preferences {
    fun getBoolean(key: String, default: Boolean): Boolean
    fun setBoolean(key: String, value: Boolean)
}

object PrefKeys {
    const val ONBOARDING_COMPLETE = "onboarding_complete"
    const val SRS_TOOLTIP_SEEN = "srs_tooltip_seen"
}
