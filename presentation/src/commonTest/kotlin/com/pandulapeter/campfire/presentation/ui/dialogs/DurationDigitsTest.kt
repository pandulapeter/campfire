/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs

import com.pandulapeter.campfire.chordpro.ChordProMetadataFields.Field
import kotlin.test.Test
import kotlin.test.assertEquals

class DurationDigitsTest {

    @Test
    fun `only digits are typed, without leading zeros, six at most`() {
        assertEquals("428", durationDigitsTyped("4:28"))
        assertEquals("5", durationDigitsTyped("005"))
        assertEquals("", durationDigitsTyped("0"))
        assertEquals("123456", durationDigitsTyped("1234567"))
    }

    @Test
    fun `digits fill in from the right`() {
        assertEquals("", durationDigitsMask(""))
        assertEquals("0:05", durationDigitsMask("5"))
        assertEquals("0:42", durationDigitsMask("42"))
        assertEquals("4:28", durationDigitsMask("428"))
        assertEquals("12:34", durationDigitsMask("1234"))
        assertEquals("1:23:45", durationDigitsMask("12345"))
    }

    @Test
    fun `a song's duration opens as the digits of its standard spelling`() {
        assertEquals("428", durationDigitsOf("268"))
        assertEquals("428", durationDigitsOf("04:28"))
        assertEquals("10203", durationDigitsOf("1:02:03"))
        assertEquals("", durationDigitsOf("about four minutes"))
        assertEquals("", durationDigitsOf(null))
    }

    @Test
    fun `digits are written in the standard spelling, carrying over what a unit cannot hold`() {
        assertEquals("", durationTextOf(""))
        assertEquals("4:28", durationTextOf("428"))
        assertEquals("1:15", durationTextOf("75"))
        assertEquals("1:00:30", durationTextOf("6030"))
        assertEquals("1:23:45", durationTextOf("12345"))
    }

    @Test
    fun `an untouched draft writes what it was opened with`() {
        val values = mapOf(Field.TITLE to "Song", Field.DURATION to "268")
        val draft = values.toSongMetadataDraft()

        assertEquals("428", draft[Field.DURATION])
        assertEquals(mapOf(Field.TITLE to "Song", Field.DURATION to "4:28"), draft.fromSongMetadataDraft())
        assertEquals("", mapOf(Field.DURATION to "soon").toSongMetadataDraft().fromSongMetadataDraft()[Field.DURATION])
    }
}
