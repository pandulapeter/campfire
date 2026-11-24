/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.launch

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.Snapshot
import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.domain.api.library.ScreenData
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.navigation.NavigationState
import com.pandulapeter.campfire.presentation.ui.navigation.SettingsTab
import com.pandulapeter.campfire.presentation.ui.playing.testPreferences
import com.pandulapeter.campfire.presentation.ui.state.setlists.ListedSetlists
import com.pandulapeter.campfire.presentation.ui.state.songs.SongGroup
import com.pandulapeter.campfire.presentation.ui.state.songs.SongGroups
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LaunchStateTest {

    private val song = Song(
        fileName = "song.cho", title = "Song", artist = "", key = null, transpose = 0,
        tags = emptyList(), languages = emptyList(), coverArtUrl = null, hasChords = false,
        canUpdateFileName = false, lastModified = 0L, size = 0L,
    )
    private val screenData = MutableStateFlow<DataState<ScreenData>>(
        DataState.Loading(
            ScreenData(setlists = emptyList(), songs = listOf(song), songSections = emptyList(), tags = emptyList(), languages = emptyList(), unfilteredSongs = listOf(song)),
        ),
    )
    private val backStack = mutableStateListOf<CampfireDestination>(CampfireDestination.Songs)

    @Test
    fun `the launch screen waits for the whole read while random picks would lead the songs list`() = runTest {
        val songGroups = MutableStateFlow(songGroups(isLibraryRead = false, areRandomPicksAwaited = true))
        val state = launchState(songGroups)
        runCurrent()
        assertFalse(state.hasLibraryToShow.value)

        songGroups.value = songGroups(isLibraryRead = true, areRandomPicksAwaited = false)
        runCurrent()
        assertTrue(state.hasLibraryToShow.value)
    }

    @Test
    fun `the launch screen goes at the first batch when no random picks are awaited`() = runTest {
        val state = launchState(MutableStateFlow(songGroups(isLibraryRead = false, areRandomPicksAwaited = false)))
        runCurrent()
        assertTrue(state.hasLibraryToShow.value)
    }

    @Test
    fun `a screen pushed over the songs list releases the wait at once`() = runTest {
        val state = launchState(MutableStateFlow(songGroups(isLibraryRead = false, areRandomPicksAwaited = true)))
        runCurrent()
        assertFalse(state.hasLibraryToShow.value)

        backStack.add(CampfireDestination.SongDetails(songFileNames = listOf(song.fileName), setlistFileName = null, initialIndex = 0))
        Snapshot.sendApplyNotifications()
        runCurrent()
        assertTrue(state.hasLibraryToShow.value)
    }

    @Test
    fun `a link the app was started with holds the launch screen until the library is read`() = runTest {
        val restored = mutableListOf<NavigationState>()
        val state = launchState(MutableStateFlow(songGroups(isLibraryRead = true, areRandomPicksAwaited = false))) { restored.add(it) }
        val opened = async { state.navigateToLink { _, _, _ -> metronome } }
        runCurrent()
        assertTrue(state.isLaunchNavigationPending.value)
        assertFalse(state.hasLibraryToShow.value)

        screenData.value = DataState.Idle(screenData.value.data!!)
        runCurrent()
        assertNull(opened.await())
        assertEquals(listOf(metronome), restored)
        assertFalse(state.isLaunchNavigationPending.value)
    }

    @Test
    fun `a link that starts the app on a restored stack replaces it`() = runTest {
        screenData.value = DataState.Idle(screenData.value.data!!)
        val restored = mutableListOf<NavigationState>()
        val state = launchState(MutableStateFlow(songGroups(isLibraryRead = true, areRandomPicksAwaited = false))) { restored.add(it) }
        backStack.add(CampfireDestination.SongDetails(songFileNames = listOf(song.fileName), setlistFileName = null, initialIndex = 0))

        assertNull(state.navigateToLink { _, _, _ -> metronome })
        assertEquals(listOf(metronome), restored)
    }

    @Test
    fun `a link is refused over the onboarding and over an import under review`() = runTest {
        screenData.value = DataState.Idle(screenData.value.data!!)
        val restored = mutableListOf<NavigationState>()
        val state = launchState(MutableStateFlow(songGroups(isLibraryRead = true, areRandomPicksAwaited = false))) { restored.add(it) }
        state.hasShownApp = true

        backStack.add(CampfireDestination.Onboarding(isOpenedFromSettings = false))
        assertEquals(LinkRefusal.ONBOARDING, state.navigateToLink { _, _, _ -> metronome })
        backStack[1] = CampfireDestination.ImportReport
        assertEquals(LinkRefusal.IMPORT, state.navigateToLink { _, _, _ -> metronome })
        assertEquals(emptyList(), restored)
    }

    @Test
    fun `a link arriving while the app is in use replaces whatever stack it is on`() = runTest {
        screenData.value = DataState.Idle(screenData.value.data!!)
        val restored = mutableListOf<NavigationState>()
        val state = launchState(MutableStateFlow(songGroups(isLibraryRead = true, areRandomPicksAwaited = false))) { restored.add(it) }
        state.hasShownApp = true
        backStack.add(CampfireDestination.Settings)

        assertNull(state.navigateToLink { _, _, _ -> metronome })
        assertEquals(listOf(metronome), restored)
        assertFalse(state.isLaunchNavigationPending.value)
    }

    @Test
    fun `a link refused over unsaved editor text says so`() = runTest {
        screenData.value = DataState.Idle(screenData.value.data!!)
        val state = launchState(MutableStateFlow(songGroups(isLibraryRead = true, areRandomPicksAwaited = false))) { false }
        state.hasShownApp = true

        assertEquals(LinkRefusal.UNSAVED_EDITOR_TEXT, state.navigateToLink { _, _, _ -> metronome })
    }

    private val metronome = NavigationState(
        backStack = listOf(CampfireDestination.Songs, CampfireDestination.Metronome),
        isSongsSearchOpen = false,
        isSetlistsSearchOpen = false,
        settingsTab = SettingsTab.HOME,
    )

    private fun songGroups(isLibraryRead: Boolean, areRandomPicksAwaited: Boolean) = SongGroups(
        filterKey = "",
        orderKey = "",
        groups = listOf(SongGroup(header = null, songs = listOf(song))),
        isLibraryRead = isLibraryRead,
        areRandomPicksAwaited = areRandomPicksAwaited,
        librarySongCount = 1,
        filteredSongCount = 1,
    )

    private fun TestScope.launchState(
        songGroups: MutableStateFlow<SongGroups>,
        restoreNavigationState: (NavigationState) -> Boolean = { false },
    ) = LaunchState(
        scope = backgroundScope,
        screenData = screenData,
        songGroups = songGroups,
        listedSetlists = MutableStateFlow(ListedSetlists.Empty),
        isDemoLibraryPending = MutableStateFlow(false),
        isEditorDraftRecoveryPending = MutableStateFlow(false),
        editorDraftRecovery = CompletableDeferred(false),
        userPreferencesState = MutableStateFlow<DataState<UserPreferences>>(DataState.Idle(testPreferences())),
        backStack = backStack,
        restoreNavigationState = restoreNavigationState,
    )
}
