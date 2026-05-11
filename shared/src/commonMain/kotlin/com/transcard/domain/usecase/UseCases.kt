package com.transcard.domain.usecase

import com.transcard.domain.model.Card
import com.transcard.domain.model.SpaceStats
import com.transcard.domain.model.StudyDirection
import com.transcard.domain.repository.CardRepository
import com.transcard.domain.repository.ProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

class GetStudyCardsUseCase(private val cardRepo: CardRepository) {
    operator fun invoke(spaceId: Long): Flow<List<Card>> {
        val now = Clock.System.now().toEpochMilliseconds()
        return cardRepo.getDueCardsBySpace(spaceId, now).map { it.shuffled() }
    }
}

class ReviewCardUseCase(
    private val cardRepo: CardRepository,
    private val progressRepo: ProgressRepository
) {
    /**
     * @param updateSrs when false, only StudyResult is written — used by Drill mode
     *                  so that "free practice" doesn't push real SRS intervals.
     */
    suspend operator fun invoke(card: Card, correct: Boolean, updateSrs: Boolean = true) {
        if (updateSrs) {
            val now = Clock.System.now().toEpochMilliseconds()
            val review = Sm2.review(card, correct, now)
            cardRepo.updateSrs(
                cardId = card.id,
                intervalDays = review.intervalDays,
                easiness = review.easiness,
                repetitions = review.repetitions,
                nextReviewAt = review.nextReviewAt
            )
        }
        progressRepo.saveResult(card.id, correct)
    }
}

class CheckAnswerUseCase {
    operator fun invoke(card: Card, input: String, direction: StudyDirection): Boolean {
        val expected = when (direction) {
            StudyDirection.NATIVE_TO_TARGET -> card.targetWord
            StudyDirection.TARGET_TO_NATIVE -> card.nativeWord
        }
        return input.trim().lowercase() == expected.trim().lowercase()
    }
}

class GetSpaceStatsUseCase(
    private val cardRepo: CardRepository,
    private val progressRepo: ProgressRepository
) {
    operator fun invoke(spaceId: Long): Flow<SpaceStats> =
        cardRepo.getCardsBySpace(spaceId)
            .combine(progressRepo.getResultsBySpace(spaceId)) { cards, results ->
                val total = cards.size
                val resultsByCard = results.groupBy { it.cardId }
                val studied = resultsByCard.size
                val correctCount = resultsByCard.values.count { res ->
                    res.firstOrNull()?.correct == true
                }
                val ratio = if (studied == 0) 0f else correctCount.toFloat() / studied
                SpaceStats(
                    totalCards = total,
                    studiedCards = studied,
                    correctRatio = ratio
                )
            }
}
