package com.transcard.domain.usecase

import com.transcard.domain.model.Card

/**
 * Simplified SM-2 spaced-repetition algorithm.
 *
 * Binary outcome (correct/incorrect) maps to SM-2 quality:
 *   correct   -> q = 4 ("good"): EF unchanged
 *   incorrect -> q < 3:           reset reps to 0, EF decreased by 0.2 (floor 1.3)
 *
 * Intervals (days):
 *   1st correct  -> 1
 *   2nd correct  -> 3
 *   3rd+ correct -> previousInterval * EF
 *   incorrect    -> 1 day from now
 */
object Sm2 {
    private const val DAY_MS = 86_400_000L
    private const val MIN_EASINESS = 1.3
    private const val DEFAULT_EASINESS = 2.5

    data class Review(
        val intervalDays: Double,
        val easiness: Double,
        val repetitions: Int,
        val nextReviewAt: Long
    )

    fun review(card: Card, correct: Boolean, now: Long): Review {
        val prevEasiness = card.easiness.takeIf { it >= MIN_EASINESS } ?: DEFAULT_EASINESS

        if (!correct) {
            return Review(
                intervalDays = 1.0,
                easiness = (prevEasiness - 0.2).coerceAtLeast(MIN_EASINESS),
                repetitions = 0,
                nextReviewAt = now + DAY_MS
            )
        }

        val newReps = card.repetitions + 1
        val newInterval = when (newReps) {
            1 -> 1.0
            2 -> 3.0
            else -> card.intervalDays * prevEasiness
        }
        return Review(
            intervalDays = newInterval,
            easiness = prevEasiness,
            repetitions = newReps,
            nextReviewAt = now + (newInterval * DAY_MS).toLong()
        )
    }
}
