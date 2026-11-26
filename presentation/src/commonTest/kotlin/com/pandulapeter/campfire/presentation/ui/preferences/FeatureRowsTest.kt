/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.preferences

import com.pandulapeter.campfire.presentation.ui.playing.testPreferences
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeatureRowsTest {

    @Test
    fun `every row is there with the defaults and before the preferences are read`() {
        listOf(testPreferences(), null).forEach { preferences ->
            assertTrue(isDockMetronomeSwitchShown(preferences))
            assertTrue(isDockTunerSwitchShown(preferences))
            assertTrue(isScaleSuggestionsSwitchShown(preferences))
            assertTrue(isChordDegreesSwitchShown(preferences))
            assertTrue(areChordSpellingRowsShown(preferences))
            assertTrue(areSongCapoRowsShown(preferences))
        }
    }

    @Test
    fun `the metronome feature off hides the dock's metronome switch`() =
        assertFalse(isDockMetronomeSwitchShown(testPreferences().copy(isMetronomeEnabled = false)))

    @Test
    fun `the scales feature off hides the scale suggestions`() =
        assertFalse(isScaleSuggestionsSwitchShown(testPreferences().copy(areScalesEnabled = false)))

    @Test
    fun `a preference turned off leaves every row there to be disabled instead`() {
        val preferences = testPreferences().copy(areChordsEnabled = false, areChordDiagramsEnabled = false)
        assertTrue(isDockTunerSwitchShown(preferences))
        assertTrue(isScaleSuggestionsSwitchShown(preferences))
        assertTrue(isChordDegreesSwitchShown(preferences))
        assertTrue(areChordSpellingRowsShown(preferences))
        assertTrue(areSongCapoRowsShown(preferences))
    }

    @Test
    fun `the degrees go only once neither a song nor a scale sheet's chords could carry them`() {
        val noSongs = testPreferences().copy(areSongsEnabled = false)
        assertTrue(isChordDegreesSwitchShown(noSongs))
        assertFalse(isChordDegreesSwitchShown(noSongs.copy(areScalesEnabled = false)))
        assertFalse(isChordDegreesSwitchShown(noSongs.copy(isChordLibraryEnabled = false)))
    }

    @Test
    fun `the capo rows go with the songs and the notation once nothing names a chord`() {
        val noSongs = testPreferences().copy(areSongsEnabled = false)
        assertFalse(areSongCapoRowsShown(noSongs))
        assertTrue(areChordSpellingRowsShown(noSongs.copy(areScalesEnabled = false)))
        assertFalse(areChordSpellingRowsShown(noSongs.copy(areScalesEnabled = false, isChordLibraryEnabled = false)))
    }
}
