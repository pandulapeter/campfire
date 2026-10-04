/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ChordProDurationTest {

    @Test
    fun `seconds, minutes and seconds, and hours are read`() {
        assertEquals(268.seconds, ChordProDuration.parse("268"))
        assertEquals(4.minutes + 28.seconds, ChordProDuration.parse("4:28"))
        assertEquals(4.minutes + 28.seconds, ChordProDuration.parse("04:28"))
        assertEquals(1.hours + 2.minutes + 3.seconds, ChordProDuration.parse("1:02:03"))
        assertEquals(125.minutes, ChordProDuration.parse("125:00"))
        assertEquals(3.minutes, ChordProDuration.parse("  3:00 "))
    }

    @Test
    fun `anything else is no duration`() {
        listOf(null, "", " ", "0", "0:00", "about 4 min", "4:5", "4:60", "4:28s", "4.28", "-3", "1:60:00", "1234567").forEach {
            assertNull(ChordProDuration.parse(it), "\"$it\"")
        }
    }

    @Test
    fun `durations are written as minutes and seconds, with hours from an hour on`() {
        assertEquals("0:05", ChordProDuration.format(5.seconds))
        assertEquals("4:28", ChordProDuration.format(268.seconds))
        assertEquals("59:59", ChordProDuration.format(3599.seconds))
        assertEquals("1:00:00", ChordProDuration.format(1.hours))
        assertEquals("2:05:09", ChordProDuration.format(2.hours + 5.minutes + 9.seconds))
    }

    @Test
    fun `a formatted duration reads back as itself`() {
        listOf(5.seconds, 268.seconds, 1.hours + 2.minutes + 3.seconds).forEach {
            assertEquals(it, ChordProDuration.parse(ChordProDuration.format(it)))
        }
    }
}
