package com.transcard.data.api

import com.transcard.config.Config
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess

class YandexDictionaryApi(private val client: HttpClient) {

    val isConfigured: Boolean = Config.YANDEX_DICT_KEY.isNotEmpty()

    /**
     * Returns translation strings for [text] in [from]→[to] direction, or null if request failed
     * (no key, network down, quota exceeded, unsupported language pair).
     */
    suspend fun lookup(text: String, from: String, to: String): List<String>? {
        if (!isConfigured) return null
        if (text.isBlank()) return emptyList()

        return runCatching {
            val response = client.get(ENDPOINT) {
                parameter("key", Config.YANDEX_DICT_KEY)
                parameter("lang", "$from-$to")
                parameter("text", text.trim())
                parameter("flags", FLAG_MORPHO)
            }
            if (!response.status.isSuccess()) return@runCatching null
            val dto: YandexDictResponseDto = response.body()
            dto.def.flatMap { def -> def.tr.map { it.text } }.distinct()
        }.getOrNull()
    }

    private companion object {
        const val ENDPOINT = "https://dictionary.yandex.net/api/v1/dicservice.json/lookup"
        const val FLAG_MORPHO = 4 // search by word form, required for translations
    }
}
