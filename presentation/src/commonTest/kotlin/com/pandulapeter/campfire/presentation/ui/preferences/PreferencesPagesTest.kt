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
import kotlin.test.assertEquals

class PreferencesPagesTest {

    @Test
    fun `settings and song details show every page, opening on the song's`() {
        listOf(PreferencesContext.SETTINGS, PreferencesContext.SONG_DETAILS).forEach { context ->
            assertEquals(
                listOf(PreferencesPage.SONG, PreferencesPage.CHORDS, PreferencesPage.METRONOME),
                preferencesPages(context = context, userPreferences = testPreferences()),
            )
        }
    }

    @Test
    fun `the chord tools show only the chords page`() {
        assertEquals(listOf(PreferencesPage.CHORDS), preferencesPages(context = PreferencesContext.CHORD_TOOLS, userPreferences = testPreferences()))
    }

    @Test
    fun `a page whose feature is switched off is left out, the chords page always staying`() {
        val everythingOff = testPreferences().copy(areSongsEnabled = false, isMetronomeEnabled = false)
        PreferencesContext.entries.forEach { context ->
            assertEquals(listOf(PreferencesPage.CHORDS), preferencesPages(context = context, userPreferences = everythingOff))
        }
        assertEquals(
            listOf(PreferencesPage.SONG, PreferencesPage.CHORDS),
            preferencesPages(context = PreferencesContext.SONG_DETAILS, userPreferences = testPreferences().copy(isMetronomeEnabled = false)),
        )
    }

    @Test
    fun `preferences not yet read show every page`() {
        assertEquals(PreferencesPage.entries, preferencesPages(context = PreferencesContext.SETTINGS, userPreferences = null))
    }
}
