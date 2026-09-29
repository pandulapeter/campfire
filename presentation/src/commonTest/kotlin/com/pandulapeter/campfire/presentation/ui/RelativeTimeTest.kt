/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui

import com.pandulapeter.campfire.presentation.ui.components.Elapsed
import com.pandulapeter.campfire.presentation.ui.components.RelativeDay
import com.pandulapeter.campfire.presentation.ui.components.elapsed
import com.pandulapeter.campfire.presentation.ui.components.nextElapsedChange
import com.pandulapeter.campfire.presentation.ui.components.nextMidnight
import com.pandulapeter.campfire.presentation.ui.components.relativeDay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class RelativeTimeTest {

    @Test
    fun `a day is counted in calendar days from today`() {
        val today = LocalDate(2026, 9, 29)
        assertEquals(RelativeDay.Today, relativeDay(today, today))
        assertEquals(RelativeDay.Tomorrow, relativeDay(LocalDate(2026, 9, 30), today))
        assertEquals(RelativeDay.Yesterday, relativeDay(LocalDate(2026, 9, 28), today))
        assertEquals(RelativeDay.InDays(5), relativeDay(LocalDate(2026, 10, 4), today))
        assertEquals(RelativeDay.DaysAgo(10), relativeDay(LocalDate(2026, 9, 19), today))
    }

    @Test
    fun `less than a day ago is rounded down to minutes or hours`() {
        val now = at(2026, 9, 29, 12, 0)
        assertEquals(Elapsed.Moments, elapsed(now - 59.seconds, now, TimeZone.UTC))
        assertEquals(Elapsed.Minutes(1), elapsed(now - 1.minutes, now, TimeZone.UTC))
        assertEquals(Elapsed.Minutes(59), elapsed(now - 59.minutes - 59.seconds, now, TimeZone.UTC))
        assertEquals(Elapsed.Hours(5), elapsed(now - 5.hours - 30.minutes, now, TimeZone.UTC))
        assertEquals(Elapsed.Hours(23), elapsed(now - 23.hours - 59.minutes, now, TimeZone.UTC))
    }

    @Test
    fun `a day or more ago counts calendar days`() {
        val now = at(2026, 9, 29, 9, 0)
        assertEquals(Elapsed.Yesterday, elapsed(at(2026, 9, 28, 1, 0), now, TimeZone.UTC))
        assertEquals(Elapsed.Days(2), elapsed(at(2026, 9, 27, 23, 0), now, TimeZone.UTC))
        assertEquals(Elapsed.Days(10), elapsed(at(2026, 9, 19, 9, 0), now, TimeZone.UTC))
    }

    @Test
    fun `a moment in the future is moments ago`() {
        val now = at(2026, 9, 29, 12, 0)
        assertEquals(Elapsed.Moments, elapsed(now + 3.hours, now, TimeZone.UTC))
    }

    @Test
    fun `the text changes next when a whole minute or hour has passed since the moment itself`() {
        val moment = at(2026, 9, 29, 12, 0) + 50.seconds
        assertEquals(moment + 1.minutes, nextElapsedChange(moment, moment + 10.seconds, TimeZone.UTC))
        assertEquals(moment + 6.minutes, nextElapsedChange(moment, moment + 5.minutes + 59.seconds, TimeZone.UTC))
        assertEquals(moment + 2.hours, nextElapsedChange(moment, moment + 1.hours, TimeZone.UTC))
        assertEquals(moment + 24.hours, nextElapsedChange(moment, moment + 23.hours + 30.minutes, TimeZone.UTC))
    }

    @Test
    fun `past a day the text changes next at midnight`() {
        val moment = at(2026, 9, 27, 12, 0)
        assertEquals(at(2026, 9, 30, 0, 0), nextElapsedChange(moment, at(2026, 9, 29, 9, 0), TimeZone.UTC))
        assertEquals(at(2026, 9, 30, 0, 0), nextMidnight(at(2026, 9, 29, 23, 59), TimeZone.UTC))
    }

    @Test
    fun `every change it announces is a change of the text`() {
        val moment = at(2026, 9, 28, 22, 17) + 13.seconds
        var now = moment
        repeat(40) {
            val next = nextElapsedChange(moment, now, TimeZone.UTC)
            assertTrue(elapsed(moment, next - 1.seconds, TimeZone.UTC) != elapsed(moment, next, TimeZone.UTC))
            now = next
        }
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int) = LocalDateTime(year, month, day, hour, minute).toInstant(TimeZone.UTC)
}
