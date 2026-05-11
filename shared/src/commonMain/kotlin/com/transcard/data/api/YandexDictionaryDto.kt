package com.transcard.data.api

import kotlinx.serialization.Serializable

@Serializable
internal data class YandexDictResponseDto(
    val def: List<YandexDefDto> = emptyList()
)

@Serializable
internal data class YandexDefDto(
    val text: String? = null,
    val pos: String? = null,
    val ts: String? = null,
    val tr: List<YandexTrDto> = emptyList()
)

@Serializable
internal data class YandexTrDto(
    val text: String,
    val pos: String? = null,
    val gen: String? = null,
    val syn: List<YandexSynDto> = emptyList()
)

@Serializable
internal data class YandexSynDto(
    val text: String
)
