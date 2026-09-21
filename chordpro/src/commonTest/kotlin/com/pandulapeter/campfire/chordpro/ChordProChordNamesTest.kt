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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChordProChordNamesTest {

    @Test
    fun `every usual spelling is a chord name`() {
        listOf("B7(b9)", "B6/9", "B6/9/F#", "Bm(maj7)", "Bmi7", "B-", "B7alt", "BΔ7", "C(add9)", "G7(#9, b13)", "(Bm7/F#)").forEach {
            assertTrue(ChordProChordNames.isChordName(it), it)
        }
    }

    @Test
    fun `prose and incomplete names are not chords`() {
        listOf("Bridge", "Halt", "Calt", "C(", "C()", "C/", "C/E/G", "E-----", "A-ha", "(x2)").forEach {
            assertFalse(ChordProChordNames.isChordName(it), it)
        }
    }

    @Test
    fun `the bass follows the last slash`() {
        assertEquals(listOf("B6/9", "F#"), ChordProChordNames.notes("B6/9/F#"))
        assertEquals("(bm7/f#)", ChordProChordNames.rewriteNotes("(Bm7/F#)") { it.lowercase() })
    }
}
