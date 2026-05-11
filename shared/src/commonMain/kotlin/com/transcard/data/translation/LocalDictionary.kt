package com.transcard.data.translation

import com.transcard.domain.model.SuggestionSource
import com.transcard.domain.model.TranslationSuggestion
import com.transcard.resources.Res
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi

@Serializable
private data class DictionaryFile(
    val version: Int = 1,
    val from: String,
    val to: String,
    val entries: Map<String, List<String>>
)

private data class IndexedDictionary(
    val forward: Map<String, List<String>>,
    val reverse: Map<String, List<String>>
)

class LocalDictionary {

    private val mutex = Mutex()
    private val cache = mutableMapOf<String, IndexedDictionary?>()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun search(query: String, from: String, to: String, limit: Int): List<TranslationSuggestion> {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) return emptyList()

        val dict = loadPair(from, to) ?: return emptyList()

        val matches = mutableListOf<String>()
        val seen = mutableSetOf<String>()

        // exact match first
        dict.forward[normalized]?.forEach {
            if (seen.add(it)) matches += it
        }

        // prefix matches
        if (matches.size < limit) {
            for ((key, values) in dict.forward) {
                if (matches.size >= limit) break
                if (key.startsWith(normalized) && key != normalized) {
                    for (v in values) {
                        if (matches.size >= limit) break
                        if (seen.add(v)) matches += v
                    }
                }
            }
        }

        return matches.map { TranslationSuggestion(it, SuggestionSource.DICTIONARY) }
    }

    @OptIn(ExperimentalResourceApi::class)
    private suspend fun loadPair(from: String, to: String): IndexedDictionary? {
        val key = "$from-$to"
        cache[key]?.let { return it }

        return mutex.withLock {
            cache[key]?.let { return@withLock it }

            // Try direct file f.ex. "ru-en.json"
            val direct = tryRead("files/dictionaries/$from-$to.json")
            if (direct != null) {
                val parsed = json.decodeFromString<DictionaryFile>(direct)
                val forward = normalize(parsed.entries)
                val reverse = buildReverse(forward)
                IndexedDictionary(forward, reverse).also { cache[key] = it }
            } else {
                // Try reverse file "to-from.json" and use its reverse index
                val swapped = tryRead("files/dictionaries/$to-$from.json")
                if (swapped != null) {
                    val parsed = json.decodeFromString<DictionaryFile>(swapped)
                    val forwardOfFile = normalize(parsed.entries)
                    val reverseOfFile = buildReverse(forwardOfFile)
                    // For our requested direction, the "forward" is reverseOfFile
                    IndexedDictionary(forward = reverseOfFile, reverse = forwardOfFile).also { cache[key] = it }
                } else {
                    cache[key] = null
                    null
                }
            }
        }
    }

    @OptIn(ExperimentalResourceApi::class)
    private suspend fun tryRead(path: String): String? = try {
        Res.readBytes(path).decodeToString()
    } catch (_: Throwable) {
        null
    }

    private fun normalize(entries: Map<String, List<String>>): Map<String, List<String>> =
        entries.mapKeys { it.key.trim().lowercase() }

    private fun buildReverse(forward: Map<String, List<String>>): Map<String, List<String>> {
        val reverse = mutableMapOf<String, MutableList<String>>()
        for ((k, values) in forward) {
            for (v in values) {
                val key = v.trim().lowercase()
                reverse.getOrPut(key) { mutableListOf() }.add(k)
            }
        }
        return reverse
    }
}
