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
        areSongsEnabled: Boolean = true,
        areSetlistsEnabled: Boolean = true,
        isMetronomeEnabled: Boolean = true,
        isTunerEnabled: Boolean = true,
        isChordLibraryEnabled: Boolean = true,
        areScalesEnabled: Boolean = true,
    ) = FeatureSwitches(
        areSongsEnabled = areSongsEnabled,
        areSetlistsEnabled = areSetlistsEnabled,
        isMetronomeEnabled = isMetronomeEnabled,
        isTunerEnabled = isTunerEnabled,
        isChordLibraryEnabled = isChordLibraryEnabled,
        areScalesEnabled = areScalesEnabled,
        areScaleSuggestionsEnabled = areScalesEnabled,
    )

    @Test
    fun `the tabs of the features switched off are left out`() {
        assertEquals(CampfireDestination.TopLevel.entries, CampfireDestination.TopLevel.entries(features()))
        assertEquals(
            listOf(CampfireDestination.Songs, CampfireDestination.Settings),
            CampfireDestination.TopLevel.entries(
                features(
                    areSetlistsEnabled = false,
                    isMetronomeEnabled = false,
                    isTunerEnabled = false,
                    isChordLibraryEnabled = false,
                    areScalesEnabled = false,
                ),
            ),
        )
        assertEquals(
            listOf(CampfireDestination.Songs, CampfireDestination.Setlists, CampfireDestination.Metronome, CampfireDestination.Chords, CampfireDestination.Settings),
            CampfireDestination.TopLevel.entries(features(isTunerEnabled = false, areScalesEnabled = false)),
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
    fun `the scales tab follows a switch of its own, whatever the chords`() {
        val preferences = testPreferences()
        assertTrue(FeatureSwitches.of(preferences).areScalesEnabled)
        assertTrue(FeatureSwitches.of(null).areScalesEnabled)
        assertTrue(FeatureSwitches.of(preferences.copy(areChordsEnabled = false, areChordDiagramsEnabled = false)).areScalesEnabled)
        assertFalse(FeatureSwitches.of(preferences.copy(areScalesEnabled = false)).areScalesEnabled)
        assertFalse(CampfireDestination.isEnabled(CampfireDestination.Scales, features(areScalesEnabled = false)))
        assertTrue(FeatureSwitches.of(preferences).areScaleSuggestionsEnabled)
        assertFalse(FeatureSwitches.of(preferences.copy(areScaleSuggestionsEnabled = false)).areScaleSuggestionsEnabled)
        assertFalse(FeatureSwitches.of(preferences.copy(areScalesEnabled = false)).areScaleSuggestionsEnabled)
        assertTrue(FeatureSwitches.of(preferences.copy(areScaleSuggestionsEnabled = false)).areScalesEnabled)
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
        val scales = songs.copy(backStack = songs.backStack + CampfireDestination.Scales)
        assertEquals(songs, scales.withoutDisabledFeatures(features(areScalesEnabled = false)))
        assertEquals(scales, scales.withoutDisabledFeatures(features(isChordLibraryEnabled = false)))
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

    @Test
    fun `the songs take the setlists with them, and the first tool left becomes home`() {
        val songsOff = FeatureSwitches.of(testPreferences().copy(areSongsEnabled = false))
        assertFalse(songsOff.areSongsEnabled)
        assertFalse(songsOff.areSetlistsEnabled)
        assertEquals(CampfireDestination.Songs, CampfireDestination.TopLevel.home(features()))
        assertEquals(CampfireDestination.Metronome, CampfireDestination.TopLevel.home(songsOff))
        assertEquals(CampfireDestination.Tuner, CampfireDestination.TopLevel.home(songsOff.copy(isMetronomeEnabled = false)))
        assertEquals(
            CampfireDestination.Settings,
            CampfireDestination.TopLevel.home(
                songsOff.copy(isMetronomeEnabled = false, isTunerEnabled = false, isChordLibraryEnabled = false, areScalesEnabled = false),
            ),
        )
        val song = CampfireDestination.SongDetails(songFileNames = listOf("a.cho"), setlistFileName = null, initialIndex = 0)
        assertFalse(CampfireDestination.isEnabled(song, songsOff))
        assertFalse(CampfireDestination.isEnabled(CampfireDestination.SongEditor(fileName = "a.cho"), songsOff))
        assertTrue(CampfireDestination.isEnabled(CampfireDestination.ImportReport, songsOff))
    }

    @Test
    fun `a stack is rooted at the home screen the switches leave`() {
        val songsOff = features(areSongsEnabled = false, areSetlistsEnabled = false)
        val song = CampfireDestination.SongDetails(songFileNames = listOf("a.cho"), setlistFileName = null, initialIndex = 0)

        assertEquals(listOf(CampfireDestination.Metronome, CampfireDestination.Settings), listOf(CampfireDestination.Songs, CampfireDestination.Settings).rootedAt(songsOff))
        assertEquals(listOf(CampfireDestination.Metronome, CampfireDestination.Tuner), listOf(CampfireDestination.Songs, CampfireDestination.Tuner).rootedAt(songsOff))
        assertEquals(listOf<CampfireDestination>(CampfireDestination.Metronome), listOf(CampfireDestination.Songs, CampfireDestination.Metronome).rootedAt(songsOff))
        assertEquals(listOf<CampfireDestination>(CampfireDestination.Metronome), listOf(CampfireDestination.Songs, song).rootedAt(songsOff))
        assertEquals(listOf<CampfireDestination>(CampfireDestination.Metronome), emptyList<CampfireDestination>().rootedAt(songsOff))
        assertEquals(listOf(CampfireDestination.Songs, CampfireDestination.Settings), listOf(CampfireDestination.Metronome, CampfireDestination.Settings).rootedAt(features()))
        assertEquals(listOf(CampfireDestination.Songs, song), listOf(CampfireDestination.Songs, song).rootedAt(features()))
    }

    @Test
    fun `a screen switched back on becomes home without being navigated to`() {
        val onlyTuner = features(
            areSongsEnabled = false,
            areSetlistsEnabled = false,
            isMetronomeEnabled = false,
            isChordLibraryEnabled = false,
            areScalesEnabled = false,
        )
        val onboarding = CampfireDestination.Onboarding(isOpenedFromSettings = true)

        assertEquals(listOf(CampfireDestination.Tuner, CampfireDestination.Settings), listOf(CampfireDestination.Settings).rootedAt(onlyTuner))
        assertEquals(
            listOf(CampfireDestination.Tuner, CampfireDestination.Settings, onboarding),
            listOf(CampfireDestination.Settings, onboarding).rootedAt(onlyTuner),
        )
        assertEquals(listOf(CampfireDestination.Songs, CampfireDestination.Tuner), listOf(CampfireDestination.Tuner).rootedAt(features()))
    }

    @Test
    fun `an address of any screen switched off opens the home screen alone`() {
        val home = NavigationState(backStack = listOf(CampfireDestination.Songs), isSongsSearchOpen = false, isSetlistsSearchOpen = false, settingsTab = SettingsTab.GENERAL)
        val allOff = features(
            areSongsEnabled = false,
            areSetlistsEnabled = false,
            isMetronomeEnabled = false,
            isTunerEnabled = false,
            isChordLibraryEnabled = false,
            areScalesEnabled = false,
        )
        val song = CampfireDestination.SongDetails(songFileNames = listOf("a.cho"), setlistFileName = null, initialIndex = 0)
        listOf(
            listOf(CampfireDestination.Songs),
            listOf(CampfireDestination.Songs, CampfireDestination.Setlists),
            listOf(CampfireDestination.Songs, CampfireDestination.Metronome),
            listOf(CampfireDestination.Songs, CampfireDestination.Tuner),
            listOf(CampfireDestination.Songs, CampfireDestination.Chords),
            listOf(CampfireDestination.Songs, CampfireDestination.Scales),
            listOf(CampfireDestination.Songs, song),
            listOf(CampfireDestination.Songs, song, CampfireDestination.SongEditor(fileName = "a.cho")),
        ).forEach { backStack ->
            assertEquals(listOf<CampfireDestination>(CampfireDestination.Settings), home.copy(backStack = backStack).withoutDisabledFeatures(allOff).backStack, "$backStack")
        }
    }

    @Test
    fun `an address with the songs switched off opens on the first tool, its search closed`() {
        val search = NavigationState(backStack = listOf(CampfireDestination.Songs), isSongsSearchOpen = true, isSetlistsSearchOpen = false, settingsTab = SettingsTab.GENERAL)
        val tuner = search.copy(backStack = search.backStack + CampfireDestination.Tuner, isSongsSearchOpen = false)
        val songsOff = features(areSongsEnabled = false, areSetlistsEnabled = false)

        assertEquals(
            search.copy(backStack = listOf(CampfireDestination.Metronome), isSongsSearchOpen = false),
            search.withoutDisabledFeatures(songsOff),
        )
        assertEquals(tuner.copy(backStack = listOf(CampfireDestination.Metronome, CampfireDestination.Tuner)), tuner.withoutDisabledFeatures(songsOff))
    }
}
