package com.transcard.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class PushRequest(
    val schemaVersion: Int,
    val payload: JsonElement,
    val clientInfo: String? = null,
)

@Serializable
data class PushResponse(val snapshotId: String, val createdAt: Long)

@Serializable
data class SnapshotEnvelope(val snapshot: SnapshotDto)

@Serializable
data class SnapshotDto(
    val id: String,
    val schemaVersion: Int,
    val payload: JsonElement,
    val sizeBytes: Int,
    val clientInfo: String? = null,
    val createdAt: Long,
)

@Serializable
data class HistoryItem(
    val id: String,
    val createdAt: Long,
    val sizeBytes: Int,
    val clientInfo: String? = null,
)
