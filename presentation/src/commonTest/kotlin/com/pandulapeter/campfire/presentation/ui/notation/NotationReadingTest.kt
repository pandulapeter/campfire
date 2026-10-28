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

import kotlin.test.Test
import kotlin.test.assertEquals

class NotationReadingTest {

    @Test
    fun `a tune is read by its title, its chords, and its words`() {
        val reading = notationReadingOf(SCARBOROUGH_FAIR, songTitle = null, chordNames = listOf("Am", "G", "Am"))
        assertEquals(NotationReading(titles = listOf("Scarborough Fair"), chords = listOf("Am", "G", "Am"), words = "Are you going to Scarborough Fair"), reading)
    }

    @Test
    fun `a tune named as the song leaves its title to the page`() {
        val reading = notationReadingOf(SCARBOROUGH_FAIR, songTitle = "scarborough  fair", chordNames = listOf("Am"))
        assertEquals(emptyList(), reading.titles)
        assertEquals("Sheet music. Chords: Am. Words: Are you going to Scarborough Fair", reading.describedAs("Sheet music", "Chords: %1\$s", "Words: %1\$s"))
    }

    @Test
    fun `a chord repeated straight after itself is said once`() {
        assertEquals(listOf("G", "C", "G"), notationReadingOf("X:1\nK:G\nG|", songTitle = null, chordNames = listOf("G", "G", "C", "G")).chords)
    }

    @Test
    fun `syllable marks are not read`() {
        assertEquals("hold the note and on-ward", notationReadingOf("X:1\nK:G\nG|\nw:hold_ the * note~and | on\\-ward", songTitle = null, chordNames = emptyList()).words)
    }

    @Test
    fun `verses are read one after the other`() {
        val tune = "X:1\nK:G\nGAB|\nw:first one\nw:second one\nw:more\nBAG|\nw:first two\n+:and on\nw:second two"
        assertEquals("first one first two and on second one second two more", notationReadingOf(tune, songTitle = null, chordNames = emptyList()).words)
    }

    @Test
    fun `the chords are said in the names the page draws`() {
        assertEquals(listOf("La-", "Sol"), notationReadingOf(SCARBOROUGH_FAIR, songTitle = null, chordNames = listOf("La-", "Sol")).chords)
    }

    @Test
    fun `a description is one sentence per part`() {
        val reading = notationReadingOf("X:1\nT:Reel\nK:D\nDEF|", songTitle = null, chordNames = listOf("D", "A"))
        assertEquals("Reel. Chords: D, A", reading.describedAs("Sheet music", "Chords: %1\$s", "Words: %1\$s"))
    }

    private companion object {
        const val SCARBOROUGH_FAIR = "X:1\nT:Scarborough Fair\nM:3/4\nL:1/8\nK:Am\n\"Am\"A2 A2 E2|\"G\"G2 G2 D2|\"Am\"A6|\nw:Are you go-ing to Scar-bo-rough Fair"
    }
}
