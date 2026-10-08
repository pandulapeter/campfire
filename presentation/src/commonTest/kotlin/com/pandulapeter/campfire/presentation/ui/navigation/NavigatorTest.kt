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

import androidx.lifecycle.SavedStateHandle
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.ui.components.SearchState
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.screens.setlists.SetlistWithSongs
import com.pandulapeter.campfire.presentation.ui.state.SavedStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavigatorTest {

    private var hasUnsavedText = false
    private var isReordering = false
    private var topLevelDestinations = CampfireDestination.TopLevel.entries
    private val savedStateHandle = SavedStateHandle()
    private val songsSearch = SearchState()
    private val setlistsSearch = SearchState()

    private fun navigator(scope: CoroutineScope, dialogHost: DialogHost = DialogHost(scope)) = Navigator(
        savedStateStore = SavedStateStore(savedStateHandle, scope),
        dialogHost = dialogHost,
        songsSearch = { songsSearch },
        setlistsSearch = { setlistsSearch },
        importReportSearch = { SearchState(isInitiallyOpen = true) },
        topLevelDestinations = { topLevelDestinations },
        userPreferences = { null as UserPreferences? },
        hasUnsavedEditorText = { hasUnsavedText },
        hasUnsavedEditorChanges = { hasUnsavedText },
        isSetlistReordering = { isReordering },
        endSetlistReordering = { isReordering = false },
        onTransitionEnded = {},
    )

    @Test
    fun `listeners hear the previous top and the new stack in the order they were added`() = runTest {
        val navigator = navigator(backgroundScope)
        val calls = mutableListOf<String>()
        navigator.addOnBackStackChanged { previousTop, stack -> calls += "first $previousTop ${stack.size}" }
        navigator.addOnBackStackChanged { _, _ -> calls += "second" }
        navigator.selectTopLevelDestination(CampfireDestination.Settings)
        assertEquals(listOf("first ${CampfireDestination.Songs} 2", "second"), calls)
    }

    @Test
    fun `a top level screen is put on top of the songs`() = runTest {
        val navigator = navigator(backgroundScope)
        navigator.selectTopLevelDestination(CampfireDestination.Metronome)
        navigator.selectTopLevelDestination(CampfireDestination.Setlists)
        assertEquals(listOf(CampfireDestination.Songs, CampfireDestination.Setlists), navigator.backStack.toList())
    }

    @Test
    fun `a switched off screen or one over unsaved editor text is not selected`() = runTest {
        val navigator = navigator(backgroundScope)
        topLevelDestinations = listOf(CampfireDestination.Songs, CampfireDestination.Settings)
        navigator.selectTopLevelDestination(CampfireDestination.Metronome)
        assertEquals(listOf<CampfireDestination>(CampfireDestination.Songs), navigator.backStack.toList())
        navigator.openSong(song("a.cho"))
        navigator.updateBackStack { add(CampfireDestination.SongEditor(fileName = "a.cho")) }
        hasUnsavedText = true
        navigator.selectTopLevelDestination(CampfireDestination.Settings)
        assertTrue(navigator.backStack.last() is CampfireDestination.SongEditor)
    }

    @Test
    fun `back ends the reorder mode before anything else`() = runTest {
        val navigator = navigator(backgroundScope)
        navigator.selectTopLevelDestination(CampfireDestination.Setlists)
        isReordering = true
        navigator.navigateBack()
        assertFalse(isReordering)
        assertEquals(2, navigator.backStack.size)
        navigator.navigateBack()
        assertEquals(1, navigator.backStack.size)
    }

    @Test
    fun `back over an editor with unsaved text asks first`() = runTest {
        val dialogHost = DialogHost(backgroundScope)
        val navigator = navigator(backgroundScope, dialogHost)
        navigator.updateBackStack { add(CampfireDestination.SongEditor(fileName = "a.cho")) }
        hasUnsavedText = true
        navigator.navigateBack()
        assertEquals(DialogType.UnsavedChanges, dialogHost.visibleDialog.value)
        assertEquals(2, navigator.backStack.size)
    }

    @Test
    fun `back from another settings tab goes to General first`() = runTest {
        val navigator = navigator(backgroundScope)
        navigator.selectTopLevelDestination(CampfireDestination.Settings)
        navigator.settingsTab = SettingsTab.ABOUT
        navigator.navigateBack()
        assertEquals(SettingsTab.GENERAL, navigator.settingsTab)
        assertEquals(2, navigator.backStack.size)
    }

    @Test
    fun `an opened file takes the place of a song pager on top rather than stacking a second one`() = runTest {
        val navigator = navigator(backgroundScope)
        navigator.openSongInSetlist(setlist("set.setlist.json", "a.cho", "b.cho"), song("b.cho"))
        navigator.openImportedSong("c.cho")
        val top = navigator.backStack.last() as CampfireDestination.SongDetails
        assertEquals(2, navigator.backStack.size)
        assertEquals(listOf("c.cho"), top.songFileNames)
        assertEquals(null, top.setlistFileName)
    }

    @Test
    fun `an opened file that is the song already on top changes nothing`() = runTest {
        val navigator = navigator(backgroundScope)
        navigator.openSong(song("a.cho"))
        val before = navigator.backStack.toList()
        navigator.openImportedSong("a.cho")
        assertEquals(before, navigator.backStack.toList())
    }

    @Test
    fun `an opened file covers a saved editor but leaves one with unsaved text alone`() = runTest {
        val navigator = navigator(backgroundScope)
        navigator.updateBackStack { add(CampfireDestination.SongEditor(fileName = "a.cho")) }
        navigator.openImportedSong("b.cho")
        assertEquals(listOf("b.cho"), (navigator.backStack.last() as CampfireDestination.SongDetails).songFileNames)
        navigator.popBackStack()
        hasUnsavedText = true
        navigator.openImportedSong("b.cho")
        assertTrue(navigator.backStack.last() is CampfireDestination.SongEditor)
    }

    @Test
    fun `a song opened over an open pager does not stack a second one`() = runTest {
        val navigator = navigator(backgroundScope)
        navigator.openSong(song("a.cho"))
        navigator.openSong(song("b.cho"))
        assertEquals(2, navigator.backStack.size)
        assertEquals(listOf("a.cho"), (navigator.backStack.last() as CampfireDestination.SongDetails).songFileNames)
    }

    @Test
    fun `pressing the open screen's item closes its search and scrolls it to the top`() = runTest {
        val navigator = navigator(backgroundScope)
        val requests = mutableListOf<CampfireDestination.TopLevel>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { navigator.scrollToTopRequests.toList(requests) }
        songsSearch.open()
        navigator.selectTopLevelDestination(CampfireDestination.Songs)
        assertFalse(songsSearch.isOpen.value)
        assertEquals(listOf<CampfireDestination.TopLevel>(CampfireDestination.Songs), requests)
    }

    @Test
    fun `pressing the open settings item scrolls it without touching a search`() = runTest {
        val navigator = navigator(backgroundScope)
        val requests = mutableListOf<CampfireDestination.TopLevel>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { navigator.scrollToTopRequests.toList(requests) }
        navigator.selectTopLevelDestination(CampfireDestination.Settings)
        songsSearch.open()
        navigator.selectTopLevelDestination(CampfireDestination.Settings)
        assertTrue(songsSearch.isOpen.value)
        assertEquals(listOf<CampfireDestination.TopLevel>(CampfireDestination.Settings), requests)
    }

    @Test
    fun `a navigation state is not restored while the editor holds unsaved text`() = runTest {
        val navigator = navigator(backgroundScope)
        navigator.updateBackStack { add(CampfireDestination.SongEditor(fileName = "a.cho")) }
        hasUnsavedText = true
        val state = NavigationState(
            backStack = listOf(CampfireDestination.Songs, CampfireDestination.Settings),
            isSongsSearchOpen = false,
            isSetlistsSearchOpen = false,
            settingsTab = SettingsTab.GENERAL,
        )
        assertFalse(navigator.restoreNavigationState(state))
        assertTrue(navigator.backStack.last() is CampfireDestination.SongEditor)
    }

    @Test
    fun `the navigation state names the page a pager has settled on`() = runTest {
        val navigator = navigator(backgroundScope)
        navigator.openSongInSetlist(setlist("set.setlist.json", "a.cho", "b.cho", "c.cho"), song("a.cho"))
        val pager = navigator.backStack.last() as CampfireDestination.SongDetails
        navigator.onSongDetailsPageSettled(pager, "c.cho")
        val reported = navigator.navigationState.backStack.last() as CampfireDestination.SongDetails
        assertEquals(2, reported.initialIndex)
        assertEquals(pager.id, reported.id)
    }

    @Test
    fun `a back stack too long to save is saved without the screen that makes it so`() = runTest {
        val navigator = navigator(backgroundScope)
        val songFileNames = (1..20_000).map { "song_number_$it.cho" }
        navigator.updateBackStack {
            add(CampfireDestination.Setlists)
            add(CampfireDestination.SongDetails(songFileNames = songFileNames, setlistFileName = "set.setlist.json", initialIndex = 0))
        }
        val saved = SavedStateStore(savedStateHandle, backgroundScope).restore<List<CampfireDestination>>(SavedStateStore.BACK_STACK_KEY)
        assertEquals(listOf(CampfireDestination.Songs, CampfireDestination.Setlists), saved)
    }

    private fun song(fileName: String) = Song(
        fileName = fileName, title = fileName, artist = "", key = null, transpose = 0, tags = emptyList(), languages = emptyList(),
        coverArtUrl = null, hasChords = true, canUpdateFileName = false, lastModified = 0, size = 0,
    )

    private fun setlist(fileName: String, vararg songFileNames: String) = SetlistWithSongs(
        setlist = Setlist(
            fileName = fileName,
            title = fileName,
            description = "",
            date = LocalDate(2026, 1, 1),
            isArchived = false,
            entries = songFileNames.map { Setlist.Entry(songFileName = it) },
            size = 0,
        ),
        entries = songFileNames.mapIndexed { index, songFileName -> SetlistWithSongs.Entry.Present(index = index, song = song(songFileName)) },
    )
}
