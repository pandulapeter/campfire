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
import com.pandulapeter.campfire.data.model.setlist.Setlist
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.ui.components.SearchState
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.screens.onboarding.OnboardingStep
import com.pandulapeter.campfire.presentation.ui.screens.setlists.SetlistWithSongs
import com.pandulapeter.campfire.presentation.ui.state.SavedStateStore
import com.pandulapeter.campfire.presentation.ui.sync.SyncConsentOrigin
import com.pandulapeter.campfire.presentation.ui.sync.showSyncConsentAnswer
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
    private var isSelecting = false
    private var topLevelDestinations = CampfireDestination.TopLevel.entries
    private var onboardingSteps = listOf(OnboardingStep.WELCOME, OnboardingStep.FEATURES, OnboardingStep.SYNC)
    private val savedStateHandle = SavedStateHandle()
    private val songsSearch = SearchState()
    private val setlistsSearch = SearchState()

    private fun navigator(scope: CoroutineScope, dialogHost: DialogHost = DialogHost(scope)) = Navigator(
        savedStateStore = SavedStateStore(savedStateHandle, scope),
        dialogHost = dialogHost,
        songsSearch = { songsSearch },
        setlistsSearch = { setlistsSearch },
        importReportSearch = { SearchState(isInitiallyOpen = true) },
        onboardingSteps = { onboardingSteps },
        topLevelDestinations = { topLevelDestinations },
        userPreferences = { null as UserPreferences? },
        hasUnsavedEditorText = { hasUnsavedText },
        hasUnsavedEditorChanges = { hasUnsavedText },
        isSetlistReordering = { isReordering },
        endSetlistReordering = { isReordering = false },
        isSongSelecting = { isSelecting },
        endSongSelection = { isSelecting = false },
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
    fun `back steps through the onboarding's pages before it leaves the screen it was opened over`() = runTest {
        val navigator = navigator(backgroundScope)
        navigator.selectTopLevelDestination(CampfireDestination.Settings)
        navigator.openOnboarding(isOpenedFromSettings = true)
        navigator.showNextOnboardingStep()
        navigator.showNextOnboardingStep()
        assertEquals(OnboardingStep.SYNC, navigator.onboardingStep)
        navigator.navigateBack()
        assertEquals(OnboardingStep.FEATURES, navigator.onboardingStep)
        navigator.navigateBack()
        assertEquals(OnboardingStep.WELCOME, navigator.onboardingStep)
        navigator.navigateBack()
        assertEquals(listOf(CampfireDestination.Songs, CampfireDestination.Settings), navigator.backStack.toList())
    }

    @Test
    fun `next on the onboarding's last page finishes it, and it opens again on its first`() = runTest {
        val navigator = navigator(backgroundScope)
        navigator.openOnboarding(isOpenedFromSettings = false)
        repeat(3) { navigator.showNextOnboardingStep() }
        assertEquals(listOf<CampfireDestination>(CampfireDestination.Songs), navigator.backStack.toList())
        navigator.openOnboarding(isOpenedFromSettings = true)
        assertEquals(OnboardingStep.WELCOME, navigator.onboardingStep)
    }

    @Test
    fun `the onboarding is opened once and closed in one step`() = runTest {
        val navigator = navigator(backgroundScope)
        navigator.openOnboarding(isOpenedFromSettings = false)
        navigator.showNextOnboardingStep()
        navigator.openOnboarding(isOpenedFromSettings = false)
        assertEquals(2, navigator.backStack.size)
        assertEquals(OnboardingStep.FEATURES, navigator.onboardingStep)
        navigator.closeOnboarding()
        assertEquals(listOf<CampfireDestination>(CampfireDestination.Songs), navigator.backStack.toList())
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
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { navigator.scrollToTopRequests.collect { requests += it.destination } }
        songsSearch.open()
        navigator.selectTopLevelDestination(CampfireDestination.Songs)
        assertFalse(songsSearch.isOpen.value)
        assertEquals(listOf<CampfireDestination.TopLevel>(CampfireDestination.Songs), requests)
    }

    @Test
    fun `pressing the open settings item scrolls it without touching a search`() = runTest {
        val navigator = navigator(backgroundScope)
        val requests = mutableListOf<CampfireDestination.TopLevel>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { navigator.scrollToTopRequests.collect { requests += it.destination } }
        navigator.selectTopLevelDestination(CampfireDestination.Settings)
        songsSearch.open()
        navigator.selectTopLevelDestination(CampfireDestination.Settings)
        assertTrue(songsSearch.isOpen.value)
        assertEquals(listOf<CampfireDestination.TopLevel>(CampfireDestination.Settings), requests)
    }

    @Test
    fun `pressing the open tool's item names the first tool for a tool at its top, but never the tool itself`() = runTest {
        val navigator = navigator(backgroundScope)
        val requests = mutableListOf<ScrollToTopRequest>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { navigator.scrollToTopRequests.toList(requests) }
        navigator.selectTopLevelDestination(CampfireDestination.Tuner)
        navigator.selectTopLevelDestination(CampfireDestination.Tuner, destinationWhenAtTop = CampfireDestination.Metronome)
        navigator.selectTopLevelDestination(CampfireDestination.Metronome)
        navigator.selectTopLevelDestination(CampfireDestination.Metronome, destinationWhenAtTop = CampfireDestination.Metronome)
        assertEquals(
            listOf(CampfireDestination.Tuner to CampfireDestination.Metronome, CampfireDestination.Metronome to null),
            requests.map { it.destination to it.destinationWhenAtTop },
        )
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

    @Test
    fun `a renamed song's screens are rewritten in place on the page they were on`() = runTest {
        val navigator = navigator(backgroundScope)
        val pager = CampfireDestination.SongDetails(songFileNames = listOf("a.cho", "b.cho"), setlistFileName = null, initialIndex = 0)
        navigator.updateBackStack { add(pager) }
        navigator.onSongDetailsPageSettled(pager, "b.cho")
        var listenerCalls = 0
        navigator.addOnBackStackChanged { _, _ -> listenerCalls++ }
        val generation = navigator.navigationGeneration
        navigator.followSongRename(from = "b.cho", to = "c.cho")
        val renamed = navigator.backStack.last() as CampfireDestination.SongDetails
        assertEquals(pager.id, renamed.id)
        assertEquals(listOf("a.cho", "c.cho"), renamed.songFileNames)
        assertEquals("c.cho", navigator.currentSongFileName(renamed))
        assertEquals(0, listenerCalls)
        assertEquals(generation, navigator.navigationGeneration)
    }

    @Test
    fun `a renamed song open in the editor is rewritten too`() = runTest {
        val navigator = navigator(backgroundScope)
        navigator.updateBackStack { add(CampfireDestination.SongEditor(fileName = "b.cho")) }
        navigator.followSongRename(from = "b.cho", to = "c.cho")
        assertEquals(CampfireDestination.SongEditor(fileName = "c.cho"), navigator.backStack.last())
    }

    @Test
    fun `deleting a song closes the editor and the details screen under it, and nothing else`() = runTest {
        val navigator = navigator(backgroundScope)
        navigator.updateBackStack {
            add(CampfireDestination.SongDetails(songFileNames = listOf("x.cho"), setlistFileName = null, initialIndex = 0))
            add(CampfireDestination.SongEditor(fileName = "x.cho"))
        }
        navigator.closeScreensShowing("x.cho")
        assertEquals(listOf<CampfireDestination>(CampfireDestination.Songs), navigator.backStack.toList())
        val other = CampfireDestination.SongDetails(songFileNames = listOf("y.cho"), setlistFileName = null, initialIndex = 0)
        navigator.updateBackStack { add(other) }
        navigator.closeScreensShowing("x.cho")
        assertEquals(listOf(CampfireDestination.Songs, other), navigator.backStack.toList())
    }

    @Test
    fun `a page of a screen that left the stack is forgotten`() = runTest {
        val navigator = navigator(backgroundScope)
        val pager = CampfireDestination.SongDetails(songFileNames = listOf("a.cho", "b.cho"), setlistFileName = null, initialIndex = 0)
        navigator.updateBackStack { add(pager) }
        navigator.onSongDetailsPageSettled(pager, "b.cho")
        navigator.popBackStack()
        navigator.updateBackStack { add(pager) }
        assertEquals("a.cho", navigator.currentSongFileName(pager))
    }

    @Test
    fun `a consent answered on the songs opens the settings on the library tab`() = runTest {
        val navigator = navigator(backgroundScope)
        navigator.showSyncConsentAnswer(SyncConsentOrigin.SETTINGS)
        assertEquals(listOf(CampfireDestination.Songs, CampfireDestination.Settings), navigator.backStack.toList())
        assertEquals(SettingsTab.LIBRARY, navigator.settingsTab)
    }

    @Test
    fun `a consent answered on the settings screen stays there and switches to the library tab`() = runTest {
        val navigator = navigator(backgroundScope)
        navigator.selectTopLevelDestination(CampfireDestination.Settings)
        val scrollToTopRequests = mutableListOf<ScrollToTopRequest>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { navigator.scrollToTopRequests.toList(scrollToTopRequests) }
        navigator.showSyncConsentAnswer(SyncConsentOrigin.SETTINGS)
        assertEquals(listOf(CampfireDestination.Songs, CampfireDestination.Settings), navigator.backStack.toList())
        assertEquals(SettingsTab.LIBRARY, navigator.settingsTab)
        assertEquals(emptyList(), scrollToTopRequests)
    }

    @Test
    fun `a consent asked from an onboarding opened in the settings reopens it on its sync page over the about tab`() = runTest {
        val navigator = navigator(backgroundScope)
        navigator.showSyncConsentAnswer(SyncConsentOrigin.ONBOARDING_FROM_SETTINGS)
        assertEquals(
            listOf(CampfireDestination.Songs, CampfireDestination.Settings, CampfireDestination.Onboarding(isOpenedFromSettings = true)),
            navigator.backStack.toList(),
        )
        assertEquals(SettingsTab.ABOUT, navigator.settingsTab)
        assertEquals(OnboardingStep.SYNC, navigator.onboardingStep)
    }

    @Test
    fun `an answer is not shown over a stack the user has built on`() = runTest {
        val navigator = navigator(backgroundScope)
        navigator.openSong(song("a.cho"))
        val stack = navigator.backStack.toList()
        navigator.showSyncConsentAnswer(SyncConsentOrigin.SETTINGS)
        assertEquals(stack, navigator.backStack.toList())
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
