package com.transcard.data.repository

import com.transcard.data.api.YandexDictionaryApi
import com.transcard.data.translation.LocalDictionary
import com.transcard.domain.model.SuggestionSource
import com.transcard.domain.model.TranslationSuggestion
import com.transcard.domain.repository.TranslationRepository

class TranslationRepositoryImpl(
    private val localDictionary: LocalDictionary,
    private val yandexApi: YandexDictionaryApi
) : TranslationRepository {

    private val onlineCache = mutableMapOf<CacheKey, List<String>>()

    override suspend fun search(
        query: String,
        from: String,
        to: String,
        limit: Int
    ): List<TranslationSuggestion> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val local = localDictionary.search(trimmed, from, to, limit)
        val online = fetchOnline(trimmed, from, to)

        val seen = local.map { it.text.lowercase() }.toMutableSet()
        val onlineUnique = online
            .asSequence()
            .filter { seen.add(it.lowercase()) }
            .take(MAX_ONLINE_SUGGESTIONS)
            .map { TranslationSuggestion(it, SuggestionSource.ONLINE_DICTIONARY) }
            .toList()

        return local + onlineUnique
    }

    private companion object {
        const val MAX_ONLINE_SUGGESTIONS = 3
    }

    private suspend fun fetchOnline(query: String, from: String, to: String): List<String> {
        if (!yandexApi.isConfigured) return emptyList()
        val key = CacheKey(query.lowercase(), from, to)
        onlineCache[key]?.let { return it }
        val fetched = yandexApi.lookup(query, from, to) ?: return emptyList()
        onlineCache[key] = fetched
        return fetched
    }

    private data class CacheKey(val text: String, val from: String, val to: String)
}
