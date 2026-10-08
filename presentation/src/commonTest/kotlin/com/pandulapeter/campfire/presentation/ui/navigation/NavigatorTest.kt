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
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.ui.components.SearchState
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.state.SavedStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavigatorTest {

    private var hasUnsavedText = false
    private var isReordering = false
    private var topLevelDestinations = CampfireDestination.TopLevel.entries

    private fun navigator(scope: CoroutineScope, dialogHost: DialogHost = DialogHost(scope)) = Navigator(
        savedStateStore = SavedStateStore(SavedStateHandle(), scope),
        dialogHost = dialogHost,
        songsSearch = { SearchState() },
        setlistsSearch = { SearchState() },
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
        navigator.openSong(song = Song(
            fileName = "a.cho", title = "A", artist = "", key = null, transpose = 0, tags = emptyList(), languages = emptyList(),
            coverArtUrl = null, hasChords = true, canUpdateFileName = false, lastModified = 0, size = 0,
        ))
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
}
