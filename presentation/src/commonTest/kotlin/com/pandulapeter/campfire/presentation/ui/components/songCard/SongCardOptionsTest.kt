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

    private val details = UserPreferences.CardDetails(isKeyShown = true, isTempoShown = null, isDurationShown = true)

    @Test
    fun `preferences not read yet show the chords and the tempo, but no cover`() {
        val options = SongCardOptions.of(userPreferences = null, details = details)

        assertTrue(options.shouldShowChords)
        assertTrue(options.isTempoShown)
        assertFalse(options.isCoverArtEnabled)
        assertEquals(UserPreferences.ChordSpelling.Default, options.chordSpelling)
    }

    @Test
    fun `a tempo the reader never picked goes with the metronome`() {
        val options = SongCardOptions.of(userPreferences = testPreferences().copy(isMetronomeEnabled = false), details = details)

        assertFalse(options.isTempoShown)
    }

    @Test
    fun `a tempo the reader picked stays whatever the metronome is`() {
        val options = SongCardOptions.of(
            userPreferences = testPreferences().copy(isMetronomeEnabled = false),
            details = details.copy(isTempoShown = true),
        )

        assertTrue(options.isTempoShown)
    }

    @Test
    fun `the list's details and the feature switches are read as they are`() {
        val options = SongCardOptions.of(
            userPreferences = testPreferences().copy(areChordsEnabled = false, isCoverArtEnabled = true),
            details = details.copy(isKeyShown = false, isDurationShown = false),
        )

        assertFalse(options.shouldShowChords)
        assertFalse(options.isKeyShown)
        assertFalse(options.isDurationShown)
        assertTrue(options.isCoverArtEnabled)
    }
}
