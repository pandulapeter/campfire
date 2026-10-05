/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.navigation

import com.pandulapeter.campfire.presentation.ui.screens.settings.SettingsTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A feature switched off takes its tab, and every screen only reached through it, out of the app. */
internal class FeatureDestinationsTest {

    @Test
    fun `the tabs of the features switched off are left out`() {
        assertEquals(CampfireDestination.TopLevel.entries, CampfireDestination.TopLevel.entries(areSetlistsEnabled = true, isMetronomeEnabled = true))
        assertEquals(
            listOf(CampfireDestination.Songs, CampfireDestination.Settings),
            CampfireDestination.TopLevel.entries(areSetlistsEnabled = false, isMetronomeEnabled = false),
        )
    }

    @Test
    fun `a song read from a setlist goes with the setlists, and one read from the library stays`() {
        val fromSetlist = CampfireDestination.SongDetails(songFileNames = listOf("a.cho"), setlistFileName = "set.setlist.json", initialIndex = 0)
        val fromLibrary = fromSetlist.copy(setlistFileName = null)

        assertFalse(CampfireDestination.isEnabled(fromSetlist, areSetlistsEnabled = false, isMetronomeEnabled = true))
        assertTrue(CampfireDestination.isEnabled(fromLibrary, areSetlistsEnabled = false, isMetronomeEnabled = false))
        assertTrue(CampfireDestination.isEnabled(CampfireDestination.Settings, areSetlistsEnabled = false, isMetronomeEnabled = false))
        assertFalse(CampfireDestination.isEnabled(CampfireDestination.Metronome, areSetlistsEnabled = true, isMetronomeEnabled = false))
    }

    @Test
    fun `an address of a feature switched off opens the songs`() {
        val songs = NavigationState(backStack = listOf(CampfireDestination.Songs), isSongsSearchOpen = false, isSetlistsSearchOpen = false, settingsTab = SettingsTab.GENERAL)
        val metronome = songs.copy(backStack = songs.backStack + CampfireDestination.Metronome)
        val setlistsSearch = songs.copy(backStack = songs.backStack + CampfireDestination.Setlists, isSetlistsSearchOpen = true)

        assertEquals(songs, metronome.withoutDisabledFeatures(areSetlistsEnabled = true, isMetronomeEnabled = false))
        assertEquals(metronome, metronome.withoutDisabledFeatures(areSetlistsEnabled = false, isMetronomeEnabled = true))
        assertEquals(songs, setlistsSearch.withoutDisabledFeatures(areSetlistsEnabled = false, isMetronomeEnabled = true))
    }

    @Test
    fun `a song read from a setlist is cut off with the setlists, and the screens above it with it`() {
        val setlistSong = CampfireDestination.SongDetails(songFileNames = listOf("a.cho"), setlistFileName = "set.setlist.json", initialIndex = 0)
        val state = NavigationState(
            backStack = listOf(CampfireDestination.Songs, CampfireDestination.Setlists, setlistSong, CampfireDestination.SongEditor(fileName = "a.cho")),
            isSongsSearchOpen = false,
            isSetlistsSearchOpen = false,
            settingsTab = SettingsTab.GENERAL,
        )

        assertEquals(listOf(CampfireDestination.Songs), state.withoutDisabledFeatures(areSetlistsEnabled = false, isMetronomeEnabled = true).backStack)
    }
}
