/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** How far a day is from today, in calendar days rather than in hours, since a setlist is for a day and not a moment. */
internal sealed interface RelativeDay {
    data object Today : RelativeDay
    data object Tomorrow : RelativeDay
    data object Yesterday : RelativeDay
    data class InDays(val days: Int) : RelativeDay
    data class DaysAgo(val days: Int) : RelativeDay
}

internal fun relativeDay(date: LocalDate, today: LocalDate) = when (val days = today.daysUntil(date)) {
    0 -> RelativeDay.Today
    1 -> RelativeDay.Tomorrow
    -1 -> RelativeDay.Yesterday
    else -> if (days > 0) RelativeDay.InDays(days) else RelativeDay.DaysAgo(-days)
}

/**
 * How long ago something happened, rounded down to the largest unit that fits. Past a day it counts calendar days
 * rather than periods of 24 hours, so that something done late last night is "Yesterday" this morning, which is how
 * anybody would say it.
 */
internal sealed interface Elapsed {
    data object Moments : Elapsed
    data class Minutes(val minutes: Int) : Elapsed
    data class Hours(val hours: Int) : Elapsed
    data object Yesterday : Elapsed
    data class Days(val days: Int) : Elapsed
}

/** A [moment] in the future, which a clock set back since it was recorded makes possible, is "moments ago" too. */
internal fun elapsed(moment: Instant, now: Instant, timeZone: TimeZone): Elapsed {
    val duration = now - moment
    return when {
        duration < 1.minutes -> Elapsed.Moments
        duration < 1.hours -> Elapsed.Minutes(duration.inWholeMinutes.toInt())
        duration < 24.hours -> Elapsed.Hours(duration.inWholeHours.toInt())
        // The day the clocks go back is 25 hours long, and 24 of them can fit inside it: that is still a day ago.
        else -> when (val days = moment.toLocalDateTime(timeZone).date.daysUntil(now.toLocalDateTime(timeZone).date).coerceAtLeast(1)) {
            1 -> Elapsed.Yesterday
            else -> Elapsed.Days(days)
        }
    }
}

/**
 * When what [elapsed] says about [moment] changes next: the next whole minute or hour counted from [moment] itself -
 * not from the clock's, or "moments ago" would stay up to a minute longer than it is true - and past a day, the next
 * midnight.
 */
internal fun nextElapsedChange(moment: Instant, now: Instant, timeZone: TimeZone): Instant {
    val duration = now - moment
    return when {
        duration < 1.minutes -> moment + 1.minutes
        duration < 1.hours -> moment + (duration.inWholeMinutes + 1).minutes
        duration < 24.hours -> moment + (duration.inWholeHours + 1).hours
        else -> nextMidnight(now, timeZone)
    }
}

internal fun nextMidnight(now: Instant, timeZone: TimeZone) = now.toLocalDateTime(timeZone).date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone)

/**
 * What [elapsed] says about [moment], kept true while it is on screen: it is worked out again the moment it would
 * change, and at least once a minute, since the wait is measured on a clock that stands still while the device
 * sleeps and knows nothing of the user moving the system clock or its time zone. A value that comes out the same is
 * the same state, so the minutely check recomposes nothing.
 */
@Composable
internal fun rememberElapsed(moment: Instant): State<Elapsed> {
    val initial = remember(moment) { elapsed(moment, Clock.System.now(), TimeZone.currentSystemDefault()) }
    return produceState(initial, moment) {
        while (true) {
            val timeZone = TimeZone.currentSystemDefault()
            val now = Clock.System.now()
            value = elapsed(moment, now, timeZone)
            delay(minOf(nextElapsedChange(moment, now, timeZone) - now, 1.minutes))
        }
    }
}

/** Today's date, kept current the way [rememberElapsed] keeps its value: at midnight, and checked every minute. */
@Composable
internal fun rememberToday(): State<LocalDate> = produceState(Clock.System.todayIn(TimeZone.currentSystemDefault())) {
    while (true) {
        val timeZone = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        value = now.toLocalDateTime(timeZone).date
        delay(minOf(nextMidnight(now, timeZone) - now, 1.minutes))
    }
}
