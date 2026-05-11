package com.transcard.domain.model

data class Space(
    val id: Long,
    val name: String,
    val nativeLang: String,
    val targetLang: String,
    val createdAt: Long
)

data class Card(
    val id: Long,
    val spaceId: Long,
    val nativeWord: String,
    val targetWord: String,
    val hint: String? = null,
    val intervalDays: Double = 0.0,
    val easiness: Double = 2.5,
    val repetitions: Int = 0,
    val nextReviewAt: Long = 0L
)

data class StudyResult(
    val cardId: Long,
    val correct: Boolean,
    val timestamp: Long
)

data class StudyResultWithSpace(
    val cardId: Long,
    val spaceId: Long,
    val correct: Boolean,
    val timestamp: Long
)

enum class StudyDirection {
    NATIVE_TO_TARGET,
    TARGET_TO_NATIVE
}

enum class StudyMode {
    /** SRS: only due cards, intervals + sad растёт. */
    SCHEDULED,
    /** Drill: все карточки в shuffle, SRS-поля не трогаем, но статистика пишется. */
    DRILL
}

data class SpaceStats(
    val totalCards: Int,
    val studiedCards: Int,
    val correctRatio: Float
)

enum class GardenStage(val emoji: String, val label: String) {
    SEED("🌰", "Семена"),
    SPROUT("🌱", "Ростки"),
    BUSH("🌿", "Кустики"),
    FLOWER("🌸", "Цветы"),
    TREE("🌳", "Деревья");

    companion object {
        fun fromReps(repetitions: Int): GardenStage = when {
            repetitions < 1 -> SEED
            repetitions < 3 -> SPROUT
            repetitions < 5 -> BUSH
            repetitions < 8 -> FLOWER
            else -> TREE
        }
    }
}

data class TranslationSuggestion(
    val text: String,
    val source: SuggestionSource
)

enum class SuggestionSource {
    USER_CARDS,
    DICTIONARY,
    ONLINE_DICTIONARY
}

data class LanguagePair(
    val code: String,
    val nativeLabel: String
) {
    companion object {
        val DEFAULT = listOf(
            LanguagePair("ru", "Русский"),
            LanguagePair("en", "Английский"),
            LanguagePair("de", "Немецкий"),
            LanguagePair("es", "Испанский"),
            LanguagePair("fr", "Французский"),
            LanguagePair("it", "Итальянский"),
            LanguagePair("ja", "Японский"),
            LanguagePair("zh", "Китайский"),
            LanguagePair("pt", "Португальский"),
            LanguagePair("tr", "Турецкий")
        )
    }
}
