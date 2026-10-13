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

import com.pandulapeter.campfire.presentation.ui.playing.testPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A feature switched off takes its tab, and every screen only reached through it, out of the app. */
internal class FeatureDestinationsTest {

    private fun features(
        areSetlistsEnabled: Boolean = true,
        isMetronomeEnabled: Boolean = true,
        isTunerEnabled: Boolean = true,
        isChordLibraryEnabled: Boolean = true,
    ) = FeatureSwitches(
        areSetlistsEnabled = areSetlistsEnabled,
        isMetronomeEnabled = isMetronomeEnabled,
        isTunerEnabled = isTunerEnabled,
        isChordLibraryEnabled = isChordLibraryEnabled,
    )

    @Test
    fun `the tabs of the features switched off are left out`() {
        assertEquals(CampfireDestination.TopLevel.entries, CampfireDestination.TopLevel.entries(features()))
        assertEquals(
            listOf(CampfireDestination.Songs, CampfireDestination.Settings),
            CampfireDestination.TopLevel.entries(
                features(areSetlistsEnabled = false, isMetronomeEnabled = false, isTunerEnabled = false, isChordLibraryEnabled = false),
            ),
        )
        assertEquals(
            listOf(CampfireDestination.Songs, CampfireDestination.Setlists, CampfireDestination.Metronome, CampfireDestination.Chords, CampfireDestination.Settings),
            CampfireDestination.TopLevel.entries(features(isTunerEnabled = false)),
        )
    }

    @Test
    fun `the chords tab follows the chord diagrams, which follow the chords`() {
        val preferences = testPreferences()
        assertTrue(FeatureSwitches.of(preferences).isChordLibraryEnabled)
        assertTrue(FeatureSwitches.of(null).isChordLibraryEnabled)
        assertFalse(FeatureSwitches.of(preferences.copy(areChordDiagramsEnabled = false)).isChordLibraryEnabled)
        assertFalse(FeatureSwitches.of(preferences.copy(areChordsEnabled = false)).isChordLibraryEnabled)
    }

    @Test
    fun `a song read from a setlist goes with the setlists, and one read from the library stays`() {
        val fromSetlist = CampfireDestination.SongDetails(songFileNames = listOf("a.cho"), setlistFileName = "set.setlist.json", initialIndex = 0)
        val fromLibrary = fromSetlist.copy(setlistFileName = null)

        assertFalse(CampfireDestination.isEnabled(fromSetlist, features(areSetlistsEnabled = false)))
        assertTrue(CampfireDestination.isEnabled(fromLibrary, features(areSetlistsEnabled = false, isMetronomeEnabled = false)))
        assertTrue(CampfireDestination.isEnabled(CampfireDestination.Settings, features(areSetlistsEnabled = false, isMetronomeEnabled = false)))
        assertFalse(CampfireDestination.isEnabled(CampfireDestination.Metronome, features(isMetronomeEnabled = false)))
        assertFalse(CampfireDestination.isEnabled(CampfireDestination.Tuner, features(isTunerEnabled = false)))
        assertFalse(CampfireDestination.isEnabled(CampfireDestination.Chords, features(isChordLibraryEnabled = false)))
    }

    @Test
    fun `an address of a feature switched off opens the songs`() {
        val songs = NavigationState(backStack = listOf(CampfireDestination.Songs), isSongsSearchOpen = false, isSetlistsSearchOpen = false, settingsTab = SettingsTab.GENERAL)
        val metronome = songs.copy(backStack = songs.backStack + CampfireDestination.Metronome)
        val setlistsSearch = songs.copy(backStack = songs.backStack + CampfireDestination.Setlists, isSetlistsSearchOpen = true)

        assertEquals(songs, metronome.withoutDisabledFeatures(features(isMetronomeEnabled = false)))
        assertEquals(metronome, metronome.withoutDisabledFeatures(features(areSetlistsEnabled = false)))
        assertEquals(songs, setlistsSearch.withoutDisabledFeatures(features(areSetlistsEnabled = false)))
        val tuner = songs.copy(backStack = songs.backStack + CampfireDestination.Tuner)
        assertEquals(songs, tuner.withoutDisabledFeatures(features(isTunerEnabled = false)))
        assertEquals(tuner, tuner.withoutDisabledFeatures(features(isMetronomeEnabled = false)))
        val chords = songs.copy(backStack = songs.backStack + CampfireDestination.Chords)
        assertEquals(songs, chords.withoutDisabledFeatures(features(isChordLibraryEnabled = false)))
        assertEquals(chords, chords.withoutDisabledFeatures(features(isTunerEnabled = false)))
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

        assertEquals(listOf(CampfireDestination.Songs), state.withoutDisabledFeatures(features(areSetlistsEnabled = false)).backStack)
    }
}
