/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songLayout

import com.pandulapeter.campfire.chordpro.model.ChordProLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChordPaddingTest {

    /** The line the way ChordPro writes it, each chord in brackets where it sits. */
    private fun lyrics(line: String): ChordProLine.Lyrics {
        val text = StringBuilder()
        val chords = mutableListOf<ChordProLine.Lyrics.Chord>()
        var index = 0
        while (index < line.length) {
            if (line[index] == '[') {
                val end = line.indexOf(']', index)
                chords += ChordProLine.Lyrics.Chord(position = text.length, name = line.substring(index + 1, end), isAnnotation = false)
                index = end + 1
            } else {
                text.append(line[index++])
            }
        }
        return ChordProLine.Lyrics(text = text.toString(), chords = chords)
    }

    /** Every character is 10 wide, and every chord [chordWidth], the gap included. */
    private fun ChordProLine.Lyrics.padded(chordWidth: Float = 45f) = padLyricsToFitChords(
        chordWidths = chords.map { chordWidth },
        gap = 0f,
        paddingWidth = 10f,
        measureWidth = { it.length * 10f },
    )

    @Test
    fun `a chord only line breaks between its chords and nowhere else`() {
        val padded = lyrics("[C] [G] [Am] [F]").padded()
        assertFalse(' ' in padded.text)
        assertEquals(3, padded.text.count { it == '​' })
        assertFalse(padded.text.endsWith('​'))
        padded.chords.drop(1).forEach { chord -> assertEquals('​', padded.text[chord.position - 1]) }
    }

    @Test
    fun `a word is never broken between its chords`() {
        assertFalse('​' in lyrics("wo[C]n[G]der").padded().text)
    }

    @Test
    fun `a padded fragment keeps its trailing space with its chord`() {
        val padded = lyrics("[C]Hello [G]world").padded(chordWidth = 100f)
        assertEquals(1, padded.text.count { it == '​' })
        assertFalse(padded.text.endsWith('​'))
        assertTrue(padded.text.startsWith("Hello "))
        assertEquals('​', padded.text[padded.chords[1].position - 1])
    }

    @Test
    fun `the inner spaces of a fragment stay ordinary`() {
        val padded = lyrics("[C]Hello world [G]x").padded(chordWidth = 30f)
        assertTrue(padded.text.startsWith("Hello world ​"))
    }

    @Test
    fun `chord positions point at their fragments`() {
        val padded = lyrics("[C]Hello [G]world [Am]again").padded(chordWidth = 100f)
        assertEquals('H', padded.text[padded.chords[0].position])
        assertEquals('w', padded.text[padded.chords[1].position])
        assertEquals('a', padded.text[padded.chords[2].position])
    }
}
