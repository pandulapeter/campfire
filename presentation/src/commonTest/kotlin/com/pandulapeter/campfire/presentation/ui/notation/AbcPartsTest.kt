/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.notation

import com.pandulapeter.campfire.data.model.preferences.UserPreferences.Instrument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AbcPartsTest {

    @Test
    fun `an exported score is one part per instrument, a grand staff and a staff with its tablature each one`() {
        assertEquals(
            listOf(
                AbcPart(name = "Vocals", voiceId = "1", score = "1"),
                AbcPart(name = "Piano", voiceId = "2", score = "{ 2 | 3 }"),
                AbcPart(name = "Guitar", voiceId = "4", score = "4 5"),
                AbcPart(name = "Bass", voiceId = "6", score = "6"),
                AbcPart(name = "Drums", voiceId = "11", score = "11"),
            ),
            abcPartsOf(EXPORT),
        )
    }

    @Test
    fun `a bracket groups instruments, each of them a part`() {
        val tune = "X:1\n%%score [(S A) (T B)]\nV:S nm=\"Soprano\"\nV:A\nV:T name=Tenor\nV:B\nK:C\n"
        assertEquals(
            listOf(
                AbcPart(name = "Soprano", voiceId = "S", score = "( S A )"),
                AbcPart(name = "Tenor", voiceId = "T", score = "( T B )"),
            ),
            abcPartsOf(tune),
        )
    }

    @Test
    fun `a tune with no score line has a part per voice, unnamed ones by their id`() {
        assertEquals(
            listOf(AbcPart(name = null, voiceId = "1", score = "1"), AbcPart(name = "Fiddle", voiceId = "2", score = "2")),
            abcPartsOf("X:1\nK:G\nV:1\nGABc|\nV:2 nm=Fiddle\nDEFG|"),
        )
    }

    @Test
    fun `a single part, voices on one staff, and several tunes offer nothing to choose`() {
        assertTrue(abcPartsOf("X:1\nK:G\nGABc|").isEmpty())
        assertTrue(abcPartsOf("X:1\n%%score (1 2)\nV:1\nV:2\nK:G\n").isEmpty())
        assertTrue(abcPartsOf("X:1\nV:1\nV:2\nK:G\n\nX:2\nV:1\nV:2\nK:D\n").isEmpty())
    }

    @Test
    fun `a tune with a part picked lists only that part in its score`() {
        assertEquals(
            "X:1\n%%score 4 5\nT:Song\nV:4\nV:5\nK:C\nC|",
            abcTuneWithPart("X:1\nT:Song\n%%score 1 { 2 | 3 } 4 5\nV:4\nV:5\nK:C\nC|", AbcPart(name = "Guitar", voiceId = "4", score = "4 5")),
        )
    }

    @Test
    fun `a part leaves the other voices' music out, so they keep no time for it`() {
        assertEquals(
            "X:1\n%%score 2\nK:C\nC|\nV:2 nm=\"Violin\"\nL:1/8\nE|",
            abcTuneWithPart("X:1\nK:C\nC|\nV:1 nm=\"Wind\"\nD|\nV:2 nm=\"Violin\"\nL:1/8\nE|\nV:1\nF|", AbcPart(name = "Violin", voiceId = "2", score = "2")),
        )
    }

    @Test
    fun `a part keeps its music written in inline voice fields, after any voice line`() {
        assertEquals(
            "X:1\n%%score 1\nK:C\nV:1 nm=\"Melody\"\n[V:1] EFGA|",
            abcTuneWithPart("X:1\nK:C\nV:1 nm=\"Melody\"\nV:2 nm=\"Bass\"\n[V:1] EFGA|\n[V:2] C,C,C,C,|", AbcPart(name = "Melody", voiceId = "1", score = "1")),
        )
    }

    @Test
    fun `voices sharing a staff are named together`() {
        assertEquals(
            listOf("Soprano / Alto", "Tenor"),
            abcPartsOf("X:1\n%%score (S A) T\nV:S nm=\"Soprano\"\nV:A nm=\"Alto\"\nV:T nm=\"Tenor\"\nK:C\n").map { it.name },
        )
    }

    @Test
    fun `the player's instrument picks its part by name, whatever its strings`() {
        val parts = abcPartsOf(EXPORT)
        assertEquals(2, abcPartFor(parts, Instrument.GUITAR_TWELVE_STRING))
        assertEquals(3, abcPartFor(parts, Instrument.BASS_FIVE_STRING))
        assertEquals(1, abcPartFor(parts, Instrument.KEYBOARD))
        assertNull(abcPartFor(parts, Instrument.UKULELE))
        assertNull(abcPartFor(parts, Instrument.OTHER))
        assertNull(abcPartFor(parts, null))
    }

    @Test
    fun `a bass guitar is the bass's, and a bassoon and a violoncello are nobody's`() {
        val parts = listOf("Bassoon", "Violoncello", "Bass Guitar", "Gitarre").map { AbcPart(name = it, voiceId = it, score = it) }
        assertEquals(2, abcPartFor(parts, Instrument.BASS))
        assertEquals(3, abcPartFor(parts, Instrument.GUITAR))
        assertNull(abcPartFor(parts, Instrument.VIOLIN))
    }

    @Test
    fun `the part shown is the instrument's, else the one picked, else the first`() {
        val parts = abcPartsOf(EXPORT)
        assertEquals(2, shownAbcPartIndex(parts, matched = 2, chosen = 4))
        assertEquals(4, shownAbcPartIndex(parts, matched = null, chosen = 4))
        assertNull(shownAbcPartIndex(parts, matched = null, chosen = ALL_PARTS))
        assertEquals(0, shownAbcPartIndex(parts, matched = null, chosen = null))
        assertEquals(0, shownAbcPartIndex(parts, matched = null, chosen = 9))
        assertNull(shownAbcPartIndex(emptyList(), matched = null, chosen = null))
    }

    private companion object {
        val EXPORT = """
            X:1
            T:Ver oor oseane
            %%score 1 { 2 | 3 } 4 5 6 11
            L:1/16
            K:Bb
            V:1 treble-8 nm="Vocals"
            V:2 treble nm="Piano"
            V:3 bass
            V:4 treble-8 nm="Guitar"
            V:5 tab stafflines=6 strings=E2,A2,D3,G3,B3,E4 nostems nm="Guitar"
            V:6 bass-8 nm="Bass"
            V:11 perc nm="Drums"
            K:none
            V:1
            z16 |
        """.trimIndent()
    }
}
