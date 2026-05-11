package com.transcard.presentation.util

private val langToCountry = mapOf(
    "ru" to "RU",
    "en" to "GB",
    "de" to "DE",
    "es" to "ES",
    "fr" to "FR",
    "it" to "IT",
    "ja" to "JP",
    "zh" to "CN",
    "pt" to "PT",
    "tr" to "TR"
)

fun languageFlag(code: String): String {
    val country = langToCountry[code.lowercase()] ?: return code.uppercase()
    val base = 0x1F1E6 - 'A'.code
    val first = country[0].uppercaseChar().code + base
    val second = country[1].uppercaseChar().code + base
    return buildString {
        appendCodePoint(first)
        appendCodePoint(second)
    }
}

private fun StringBuilder.appendCodePoint(cp: Int): StringBuilder {
    if (cp <= 0xFFFF) {
        append(cp.toChar())
    } else {
        val offset = cp - 0x10000
        val high = (offset shr 10) + 0xD800
        val low = (offset and 0x3FF) + 0xDC00
        append(high.toChar())
        append(low.toChar())
    }
    return this
}
