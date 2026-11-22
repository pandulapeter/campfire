/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.playing

import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.chordpro.chords.ChordProChords
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.ui.chords.namedAsShapes
import com.pandulapeter.campfire.presentation.ui.chords.songChordsOf
import com.pandulapeter.campfire.presentation.ui.rendering.testSongRenderer
import com.pandulapeter.campfire.presentation.ui.songLayout.DefaultSectionLabels
import com.pandulapeter.campfire.presentation.ui.songView.model.prepareSongLyrics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SongReadingTest {

    private val renderer = testSongRenderer()
    private val spelling = UserPreferences.ChordSpelling.Default
    private val shapes = ShapeNames(capo = 2, shapesKey = "Em", soundingKey = "F#m", inferredChordsKey = null)
    private val capoChart = """
        {title: Capo chart}
        {key: Em}
        {capo: 2}

        [Em]One [C]two [G]three [D]four

        {key: Am}
        [Am]Five [F]six [C]seven [G]eight

        {transpose: 2}
        [Am]Nine [F]ten
    """.trimIndent()
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
        abc = "ABC notation",
        lilyPond = "LilyPond",
        svg = "SVG",
    )

    private fun readingOf(text: String, shapeNames: ShapeNames?, inferredChordsKey: String? = null) = renderer.songReadingOf(
        text = text,
        transposition = 2,
        tabCapo = 0,
        spelling = spelling,
        tabs = null,
        shapeNames = shapeNames,
        inferredChordsKey = inferredChordsKey,
    )

    private fun ChordProSong.chordNames() = blocks.filterIsInstance<ChordProBlock.Section>()
        .flatMap { it.lines }
        .filterIsInstance<ChordProLine.Lyrics>()
        .flatMap { line -> line.chords.filterNot { it.isAnnotation }.map { it.name } }

    private fun ChordProSong.keyChanges() = blocks.filterIsInstance<ChordProBlock.Transpose>().mapNotNull { it.key }

    private fun pitchesOf(name: String, movedBy: Int = 0) = ChordProChords.parse(name)!!.let { chord ->
        (chord.root + movedBy).mod(12) to chord.pitchClasses.map { (it + movedBy).mod(12) }.toSet()
    }

    @Test
    fun `a capo chart read by its shapes sounds raised and is named by the shapes written`() {
        val reading = readingOf(capoChart, shapes)
        assertEquals(listOf("F#m", "D", "A", "E"), reading.transposed.chordNames().take(4))
        assertEquals(listOf("Em", "C", "G", "D"), reading.named.chordNames().take(4))
        assertEquals(2, reading.nameCapo)
    }

    @Test
    fun `the key changes further down are applied once in each reading`() {
        val reading = readingOf(capoChart, shapes)
        val sounding = reading.transposed.chordNames()
        val named = reading.named.chordNames()
        assertEquals(sounding.size, named.size)
        named.zip(sounding).forEach { (shape, sound) -> assertEquals(pitchesOf(sound), pitchesOf(shape, movedBy = 2), "$shape under $sound") }
        val soundingKeys = reading.transposed.keyChanges()
        val namedKeys = reading.named.keyChanges()
        assertTrue(soundingKeys.isNotEmpty())
        assertEquals(soundingKeys.size, namedKeys.size)
        namedKeys.zip(soundingKeys).forEach { (shape, sound) -> assertEquals(pitchesOf(sound), pitchesOf(shape, movedBy = 2), "$shape under $sound") }
    }

    @Test
    fun `a song named as it sounds is read once`() {
        val reading = readingOf(capoChart, shapeNames = null, inferredChordsKey = "G")
        assertSame(reading.transposed, reading.named)
        assertEquals(0, reading.nameCapo)
        assertEquals("G", reading.namedInferredKey)
    }

    @Test
    fun `the shown song names the key that sounds`() {
        val shown = renderer.shownSongOf(readingOf(capoChart, shapes), spelling, tempo = null, capo = null, inferredKey = null)
        assertEquals("F#m", shown.metadata.key)
    }

    @Test
    fun `a song naming no key is shown in the inferred one`() {
        val keyless = "{title: No key}\n\n[G]One [C]two [D]three"
        assertEquals("G", renderer.shownSongOf(readingOf(keyless, shapeNames = null), spelling, tempo = null, capo = null, inferredKey = "G").metadata.key)
    }

    @Test
    fun `a song naming its key keeps it`() {
        val keyed = "{title: Keyed}\n{key: C}\n\n[C]One [F]two [G]three"
        val shown = renderer.shownSongOf(readingOf(keyed, shapeNames = null), spelling, tempo = null, capo = null, inferredKey = "G")
        assertEquals("D", shown.metadata.key)
    }

    @Test
    fun `the chord shape sheet names the chords as the page does`() {
        val reading = readingOf(capoChart, shapes)
        val sheet = songChordsOf(reading.transposed, ChordNotation.STANDARD, ChordTuning.GUITAR, capo = 2, inferredKey = null)
            .namedAsShapes(reading.named, ChordNotation.STANDARD, reading.nameCapo, reading.namedInferredKey)
        val page = prepareSongLyrics(
            song = renderer.shownSongOf(reading, spelling, tempo = null, capo = null, inferredKey = null),
            shouldShowChords = true,
            labels = labels,
            chordTuning = ChordTuning.GUITAR,
            standardSong = reading.transposed,
            shapeCapo = 2,
            namedSong = reading.named,
            nameCapo = reading.nameCapo,
            namedInferredKey = reading.namedInferredKey,
            shapesKey = reading.shapeNames?.shapesKey,
        )
        assertTrue(sheet.isNotEmpty())
        assertEquals(sheet.map { it.name }.sorted(), page.chords.map { it.name }.sorted())
    }
}
