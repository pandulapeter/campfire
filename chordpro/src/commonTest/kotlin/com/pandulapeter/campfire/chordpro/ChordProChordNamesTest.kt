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
import kotlin.test.assertNull
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

    @Test
    fun `a lowercase root is a minor chord`() {
        assertEquals("Am", ChordProChordNames.lowercaseMinorExpanded("a"))
        assertEquals("Hm7", ChordProChordNames.lowercaseMinorExpanded("h7"))
        assertEquals("F#m", ChordProChordNames.lowercaseMinorExpanded("f#"))
        assertEquals("(Em)", ChordProChordNames.lowercaseMinorExpanded("(e)"))
        assertEquals("Em/G", ChordProChordNames.lowercaseMinorExpanded("e/G"))
        listOf("am", "amaj7", "add", "fine", "A", "N.C.").forEach { assertNull(ChordProChordNames.lowercaseMinorExpanded(it), it) }
        assertEquals("c#7", ChordProChordNames.lowercaseMinorFolded("C#m7"))
        assertEquals("(bb)", ChordProChordNames.lowercaseMinorFolded("(Bbm)"))
    }

    @Test
    fun `a bass note may be written in lowercase`() {
        listOf("D/f#", "A/c#", "C/h", "Dm/f#").forEach { assertTrue(ChordProChordNames.isChordName(it), it) }
        assertEquals(listOf("D", "f#"), ChordProChordNames.notes("D/f#"))
    }

    @Test
    fun `a lowercase root is still not a chord on its own`() {
        listOf("a", "h7", "f#", "break").forEach { assertFalse(ChordProChordNames.isChordName(it), it) }
    }

    @Test
    fun `a lowercase minor with a lowercase bass expands`() {
        assertEquals("Dm/f#", ChordProChordNames.lowercaseMinorExpanded("d/f#"))
    }
}
