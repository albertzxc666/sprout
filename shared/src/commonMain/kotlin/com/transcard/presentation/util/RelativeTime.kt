package com.transcard.presentation.util

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime

/**
 * Returns a short Russian relative-time label for a past timestamp.
 * Granularity: today / yesterday / N days ago / weeks / months.
 */
fun relativeTimeRu(timestampMs: Long): String {
    val tz = TimeZone.currentSystemDefault()
    val then = Instant.fromEpochMilliseconds(timestampMs).toLocalDateTime(tz).date
    val today = Clock.System.now().toLocalDateTime(tz).date
    val days = then.daysUntil(today)
    return when {
        days <= 0 -> "сегодня"
        days == 1 -> "вчера"
        days < 7 -> "$days ${pluralRu(days, "день", "дня", "дней")} назад"
        days < 30 -> {
            val w = days / 7
            "$w ${pluralRu(w, "неделю", "недели", "недель")} назад"
        }
        else -> {
            val m = days / 30
            "$m ${pluralRu(m, "месяц", "месяца", "месяцев")} назад"
        }
    }
}

/**
 * Human-readable Russian forecast for an SRS interval.
 *   1d  -> "завтра"
 *   3d  -> "через 3 дня"
 *  10d  -> "через 1 неделю"
 *  60d  -> "через 2 месяца"
 */
fun humanizeIntervalRu(days: Double): String {
    val d = days.toInt().coerceAtLeast(1)
    return when {
        d == 1 -> "завтра"
        d < 7 -> "через $d ${pluralRu(d, "день", "дня", "дней")}"
        d < 30 -> {
            val w = d / 7
            "через $w ${pluralRu(w, "неделю", "недели", "недель")}"
        }
        else -> {
            val m = d / 30
            "через $m ${pluralRu(m, "месяц", "месяца", "месяцев")}"
        }
    }
}

fun pluralRu(n: Int, one: String, few: String, many: String): String {
    val mod10 = n % 10
    val mod100 = n % 100
    return when {
        mod100 in 11..14 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
}
