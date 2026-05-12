package com.transcard.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class SnapshotPayload(
    val schemaVersion: Int,
    val exportedAt: Long,
    val spaces: List<SpaceSnapshotDto>,
    val cardGroups: List<CardGroupSnapshotDto>,
    val cards: List<CardSnapshotDto>,
    val studyResults: List<StudyResultSnapshotDto>,
)

@Serializable
data class SpaceSnapshotDto(
    val id: Long,
    val name: String,
    val nativeLang: String,
    val targetLang: String,
    val createdAt: Long,
)

@Serializable
data class CardGroupSnapshotDto(
    val id: Long,
    val spaceId: Long,
    val name: String,
    val createdAt: Long,
)

@Serializable
data class CardSnapshotDto(
    val id: Long,
    val spaceId: Long,
    val groupId: Long,
    val nativeWord: String,
    val targetWord: String,
    val hint: String? = null,
    val intervalDays: Double,
    val easiness: Double,
    val repetitions: Int,
    val nextReviewAt: Long,
)

@Serializable
data class StudyResultSnapshotDto(
    val id: Long,
    val cardId: Long,
    val correct: Boolean,
    val timestamp: Long,
)
