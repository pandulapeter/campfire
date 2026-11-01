/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.presentation.localization.joinIsolated
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.dialogs.songDisplay.example
import com.pandulapeter.campfire.presentation.ui.dialogs.songDisplay.exampleChords
import com.pandulapeter.campfire.presentation.ui.playing.testPreferences
import com.pandulapeter.campfire.presentation.ui.rendering.testSongRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AskForChordNamesOnceTest {

    private val unasked = testPreferences().copy(isSongLayoutChosen = true)

    private fun isOwed(
        preferences: UserPreferences? = unasked,
        hasChords: Boolean = true,
        capo: Int = 2,
        isCapoChanged: Boolean = false,
        isPerformanceModeEnabled: Boolean = false,
    ) = isChordNamesQuestionOwed(
        preferences = preferences,
        hasChords = hasChords,
        capo = capo,
        isCapoChanged = isCapoChanged,
        isPerformanceModeEnabled = isPerformanceModeEnabled,
    )

    @Test
    fun `a song opened with a capo asks, one opened without a capo does not`() {
        assertTrue(isOwed(capo = 2))
        assertFalse(isOwed(capo = 0))
    }

    @Test
    fun `a capo stepped while the song is read asks, to none included`() {
        assertTrue(isOwed(capo = 1, isCapoChanged = true))
        assertTrue(isOwed(capo = 0, isCapoChanged = true))
    }

    @Test
    fun `it waits for the layout question, which the first song asks, so the two never come together`() {
        assertFalse(isOwed(preferences = unasked.copy(isSongLayoutChosen = false)))
        assertTrue(isOwed(preferences = unasked.copy(isSongLayoutChosen = true)))
    }

    @Test
    fun `a reader updating from a version that never asked it is asked over a capoed song`() {
        // Every field an older version wrote, and nothing of the chord names, which their defaults then stand for (the
        // document's own reading of such a file is tested in the local source's UserPreferencesMappersTest).
        val updated = testPreferences().copy(isSongLayoutChosen = true, instrument = UserPreferences.Instrument.GUITAR)
        assertTrue(isOwed(preferences = updated, capo = 2))
    }

    @Test
    fun `it is asked once, a pick in Preferences counting as the answer`() {
        assertFalse(isOwed(preferences = unasked.copy(isChordNamesChosen = true)))
    }

    @Test
    fun `it is not asked where the answer changes nothing on screen`() {
        assertFalse(isOwed(preferences = null))
        assertFalse(isOwed(preferences = unasked.copy(areChordsEnabled = false)))
        assertFalse(isOwed(preferences = unasked.copy(instrument = UserPreferences.Instrument.KEYBOARD)))
        assertFalse(isOwed(hasChords = false))
    }

    @Test
    fun `performance mode is not interrupted by it`() {
        assertFalse(isOwed(isPerformanceModeEnabled = true))
    }

    @Test
    fun `the question names the song's key both ways only where the capo tells them apart`() {
        assertEquals("Em" to "F#m", DialogType.ChordNames(capo = 2, shapesKey = "Em", soundingKey = "F#m").example())
        assertNull(DialogType.ChordNames(capo = 0, shapesKey = "Em", soundingKey = "Em").example())
        assertNull(DialogType.ChordNames(capo = 2, shapesKey = null, soundingKey = null).example())
    }

    private val capoChart = Song(
        fileName = "capo.cho",
        title = "Capo",
        artist = "",
        key = "Em",
        transpose = 0,
        tags = emptyList(),
        languages = emptyList(),
        coverArtUrl = null,
        hasChords = true,
        canUpdateFileName = false,
        lastModified = 0,
        size = 0,
        capo = 2,
    )
    private val capoChartText = "{title: Capo}\n{key: Em}\n{capo: 2}\n[Em]One [C]two [D]three [Bm]four [Em]five [G]six\n"

    private fun questionOf(capo: Int = 2, transposition: Int = 0, notation: UserPreferences.Notation = UserPreferences.Notation.STANDARD) =
        chordNamesQuestionOf(
            renderer = testSongRenderer(),
            song = capoChart,
            text = capoChartText,
            transposition = transposition,
            capo = capo,
            spelling = UserPreferences.ChordSpelling.Default.copy(notation = notation),
            inferredKey = null,
        )

    @Test
    fun `the cards name the song's first four chords by the shapes and as they sound`() {
        val question = questionOf()
        assertEquals(listOf("Em", "C", "D", "Bm"), question.shapesChords)
        assertEquals(listOf("F♯m", "D", "E", "C♯m"), question.soundingChords)
        assertEquals("Em" to "F♯m", question.shapesKey to question.soundingKey)
    }

    @Test
    fun `the chords are in the reader's notation and transposition`() {
        val german = questionOf(notation = UserPreferences.Notation.GERMAN)
        assertEquals(listOf("Em", "C", "D", "Hm"), german.shapesChords)
        assertEquals(listOf("F♯m", "D", "E", "C♯m"), german.soundingChords)
        val transposed = questionOf(transposition = 2)
        assertEquals(listOf("F♯m", "D", "E", "C♯m"), transposed.shapesChords)
        assertEquals(listOf("G♯m", "E", "F♯", "D♯m"), transposed.soundingChords)
    }

    @Test
    fun `the cards show no chords where the two ways read the same`() {
        assertNull(DialogType.ChordNames(capo = 2, shapesKey = null, soundingKey = null).exampleChords())
        assertNull(DialogType.ChordNames(capo = 0, shapesKey = null, soundingKey = null, shapesChords = listOf("G"), soundingChords = listOf("G")).exampleChords())
        assertEquals(
            listOf("Em").joinIsolated(" · ") to listOf("F♯m").joinIsolated(" · "),
            DialogType.ChordNames(capo = 2, shapesKey = null, soundingKey = null, shapesChords = listOf("Em"), soundingChords = listOf("F♯m")).exampleChords(),
        )
    }
}
