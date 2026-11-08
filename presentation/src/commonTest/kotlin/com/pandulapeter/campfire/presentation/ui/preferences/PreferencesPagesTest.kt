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
    fun `song details shows every page, general first`() = assertEquals(
        listOf(PreferencesPage.GENERAL, PreferencesPage.SONG, PreferencesPage.CHORDS, PreferencesPage.METRONOME),
        preferencesPages(context = PreferencesContext.SONG_DETAILS, userPreferences = testPreferences()),
    )

    @Test
    fun `the chord tools show the general and the chords pages`() {
        assertEquals(listOf(PreferencesPage.GENERAL, PreferencesPage.CHORDS), preferencesPages(context = PreferencesContext.CHORD_TOOLS, userPreferences = testPreferences()))
    }

    @Test
    fun `a page whose feature is switched off is left out, the general and the chords pages always staying`() {
        val everythingOff = testPreferences().copy(areSongsEnabled = false, isMetronomeEnabled = false)
        PreferencesContext.entries.forEach { context ->
            assertEquals(listOf(PreferencesPage.GENERAL, PreferencesPage.CHORDS), preferencesPages(context = context, userPreferences = everythingOff))
        }
        assertEquals(
            listOf(PreferencesPage.GENERAL, PreferencesPage.SONG, PreferencesPage.CHORDS),
            preferencesPages(context = PreferencesContext.SONG_DETAILS, userPreferences = testPreferences().copy(isMetronomeEnabled = false)),
        )
    }

    @Test
    fun `preferences not yet read show every page`() {
        assertEquals(PreferencesPage.entries, preferencesPages(context = PreferencesContext.SONG_DETAILS, userPreferences = null))
    }
}
