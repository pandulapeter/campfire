/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.songCard

import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.ui.playing.testPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SongCardOptionsTest {

    @Test
    fun `preferences not read yet show the chords and the tempo, but no cover`() {
        val options = SongCardOptions.forSongs(userPreferences = null)

        assertTrue(options.shouldShowChords)
        assertTrue(options.isTempoShown)
        assertFalse(options.isCoverArtEnabled)
        assertEquals(UserPreferences.ChordSpelling.Default, options.chordSpelling)
    }

    @Test
    fun `the tempo goes with the metronome`() {
        val preferences = testPreferences().copy(isMetronomeEnabled = false)

        assertFalse(SongCardOptions.forSongs(userPreferences = preferences).isTempoShown)
        assertFalse(SongCardOptions.forSetlists(userPreferences = preferences).isTempoShown)
        assertTrue(SongCardOptions.forSetlists(userPreferences = testPreferences()).isTempoShown)
    }

    @Test
    fun `only a setlist's cards show the duration`() {
        assertFalse(SongCardOptions.forSongs(userPreferences = testPreferences()).isDurationShown)
        assertTrue(SongCardOptions.forSetlists(userPreferences = testPreferences()).isDurationShown)
    }

    @Test
    fun `the feature switches are read as they are`() {
        val options = SongCardOptions.forSetlists(
            userPreferences = testPreferences().copy(areChordsEnabled = false, isCoverArtEnabled = true),
        )

        assertFalse(options.shouldShowChords)
        assertTrue(options.isCoverArtEnabled)
    }
}
