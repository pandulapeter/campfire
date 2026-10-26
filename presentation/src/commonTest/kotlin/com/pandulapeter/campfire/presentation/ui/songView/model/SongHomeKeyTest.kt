/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView.model

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.chordpro.chords.ChordProTransposer
import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.chordpro.scales.ScaleGroup
import com.pandulapeter.campfire.chordpro.scales.Scales
import com.pandulapeter.campfire.chordpro.scales.SongKeys
import com.pandulapeter.campfire.presentation.ui.songLayout.DefaultSectionLabels
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SongHomeKeyTest {

    private val labels = DefaultSectionLabels(
        verse = "Verse",
        chorus = "Chorus",
        bridge = "Bridge",
        tab = "Tab",
        grid = "Grid",
        intro = "Intro",
        preChorus = "Pre-chorus",
        solo = "Solo",
        outro = "Outro",
    )

    private fun scale(id: String) = Scales.all.first { it.id == id }

    private fun home(rootName: String, scaleId: String, isHome: Boolean = true) =
        ScaleGroup(blocks = emptyList(), rootName = rootName, scales = listOf(scale(scaleId)), isHome = isHome)

    private fun model(
        text: String,
        notation: ChordNotation = ChordNotation.STANDARD,
        transposition: Int = 0,
        showsDegrees: Boolean = true,
    ) = ChordProTransposer.transpose(ChordProParser.parse(text), transposition).let { song ->
        prepareSongLyrics(
            song = song,
            shouldShowChords = true,
            labels = labels,
            chordTuning = ChordTuning.GUITAR,
            notation = notation,
            showsDegrees = showsDegrees,
        )
    }

    @Test
    fun `a home's key is its root, minor where its scale has only the minor third`() {
        assertEquals("D", homeKeyOf(listOf(home("D", "major"))))
        assertEquals("D", homeKeyOf(listOf(home("D", "mixolydian"))))
        assertEquals("Dm", homeKeyOf(listOf(home("D", "dorian"))))
        assertEquals("F#m", homeKeyOf(listOf(home("F#", "natural_minor"))))
        assertEquals("Em", homeKeyOf(listOf(home("E", "minor_pentatonic"))))
    }

    @Test
    fun `a song with no home has no key of its own`() {
        assertNull(homeKeyOf(emptyList()))
        assertNull(homeKeyOf(listOf(home("D", "major", isHome = false), home("E", "major", isHome = false))))
    }

    @Test
    fun `each diagram names its step of the key the chords are found in, with no key written and the scales off`() {
        val major = model("[C]la [Dm]la [G7]la [C]la [F]la [Bb]la [C]la")
        assertEquals(listOf("I", "ii", "V7", "IV", "♭VII"), major.chords.map { it.degree })
        assertEquals(emptyList(), major.scaleGroups)

        val minor = model("[Am]la [G]la [F]la [E7]la [Am]la [G]la [Am]la")
        assertEquals(listOf("i", "♭VII", "♭VI", "V7"), minor.chords.map { it.degree })
    }

    @Test
    fun `the degrees do not move with the transposition`() {
        val text = "[C]la [Dm]la [G7]la [C]la [F]la [C]la"
        assertEquals(model(text).chords.map { it.degree }, model(text, transposition = 3).chords.map { it.degree })
    }

    @Test
    fun `a capo chart is numbered in the key of its shapes`() {
        val capoed = model("{key: G}\n{capo: 5}\n[D]la [G]la [A]la [D]la [G]la [A7]la [D]la")
        assertEquals(listOf("I", "IV", "V", "V7"), capoed.chords.map { it.degree })
    }

    @Test
    fun `a capo chart sounds and is numbered in its sounding key, whatever capo it is played at`() {
        val text = "{key: G}\n{capo: 5}\n[D]la [G]la [A]la [D]la [G]la [A7]la [D]la"
        // Sounding: the file's capo counted into the chords, as the page reads it, its key read as its chords'.
        val sounding = SongKeys.withKeyOfChords(ChordProParser.parse(text)).let { ChordProTransposer.transpose(it, 5) }
        listOf(0, 2, 5, 7).forEach { capo ->
            val played = prepareSongLyrics(
                song = sounding,
                shouldShowChords = true,
                labels = labels,
                chordTuning = ChordTuning.GUITAR,
                showsDegrees = true,
                suggestsScales = true,
                shapeCapo = capo,
            )
            assertEquals("G", sounding.metadata.key)
            assertEquals(listOf("G", "C", "D", "D7"), played.chords.map { it.name })
            assertEquals(listOf("I", "IV", "V", "V7"), played.chords.map { it.degree })
            assertEquals("G", played.scaleGroups.first().rootName, "capo $capo")
        }
    }

    @Test
    fun `a numbering names no degree, nor does a switched off label`() {
        val text = "{key: C}\n[C]la [F]la [G]la [C]la"
        assertEquals(listOf(null, null, null), model(text, notation = ChordNotation.ROMAN).chords.map { it.degree })
        assertEquals(listOf(null, null, null), model(text, showsDegrees = false).chords.map { it.degree })
    }

    @Test
    fun `the key is inferred only for a song whose file names none`() {
        assertEquals("C", inferredKeyOf(ChordProParser.parse("[C]la [F]la [G]la [C]la")))
        assertEquals("Am", inferredKeyOf(ChordProParser.parse("[Am]la [G]la [F]la [E7]la [Am]la")))
        assertNull(inferredKeyOf(ChordProParser.parse("{key: G}\n[C]la [F]la [G]la [C]la")))
    }
}
