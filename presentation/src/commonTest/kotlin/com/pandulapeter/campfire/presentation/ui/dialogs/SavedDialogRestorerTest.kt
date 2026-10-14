/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.SongContent
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.state.SavedStateStore
import com.pandulapeter.campfire.presentation.ui.state.SongLookup
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The restorer against small fakes, moved with runCurrent, since everything it launches runs in the background scope. */
class SavedDialogRestorerTest {

    private val backStack = mutableStateListOf<CampfireDestination>(CampfireDestination.Songs)
    private val songLookup = MutableStateFlow(SongLookup.Empty)
    private val setlists = MutableStateFlow(emptyList<Setlist>())
    private lateinit var savedStateStore: SavedStateStore
    private lateinit var dialogHost: DialogHost

    private fun TestScope.restorer(saved: SavedDialog): SavedDialogRestorer {
        savedStateStore = SavedStateStore(SavedStateHandle(), backgroundScope)
        savedStateStore.persist<SavedDialog?>(SavedStateStore.VISIBLE_DIALOG_KEY, saved)
        dialogHost = DialogHost(backgroundScope)
        return SavedDialogRestorer(
            scope = backgroundScope,
            savedStateStore = savedStateStore,
            dialogHost = dialogHost,
            backStack = backStack,
            songLookup = songLookup,
            setlists = setlists,
            editorDraft = MutableStateFlow<SongContent?>(null),
            songTexts = MutableStateFlow(emptyMap()),
            loadSongContent = { Job().apply { complete() } },
            duplicateSongDialogOf = { null },
            showSavedSongEdit = { _, _ -> },
        )
    }

    private val savedDialog get() = savedStateStore.restore<SavedDialog?>(SavedStateStore.VISIBLE_DIALOG_KEY)

    private fun readLibrary() {
        songLookup.value = SongLookup(emptyMap(), isLibraryRead = true)
    }

    @Test
    fun `a saved new setlist sheet is reopened once the library is read`() = runTest {
        restorer(SavedDialog.NewSetlist).start()
        runCurrent()
        assertNull(dialogHost.visibleDialog.value)
        readLibrary()
        runCurrent()
        assertEquals(DialogType.NewSetlist, dialogHost.visibleDialog.value)
        assertEquals(SavedDialog.NewSetlist, savedDialog)
    }

    @Test
    fun `a saved sheet is given up once another screen is on top`() = runTest {
        restorer(SavedDialog.NewSetlist).start()
        runCurrent()
        backStack.add(CampfireDestination.Settings)
        Snapshot.sendApplyNotifications()
        runCurrent()
        readLibrary()
        runCurrent()
        assertNull(dialogHost.visibleDialog.value)
        assertNull(savedDialog)
    }

    @Test
    fun `a saved sheet gives way to a dialog already up and is forgotten`() = runTest {
        val restorer = restorer(SavedDialog.NewSetlist)
        dialogHost.showDialog(DialogType.WhatsNew)
        restorer.start()
        readLibrary()
        runCurrent()
        assertEquals(DialogType.WhatsNew, dialogHost.visibleDialog.value)
        assertNull(savedDialog)
    }

    @Test
    fun `a sheet over a setlist that is gone is forgotten`() = runTest {
        restorer(SavedDialog.EditSetlist("missing.setlist.json")).start()
        readLibrary()
        runCurrent()
        assertNull(dialogHost.visibleDialog.value)
        assertNull(savedDialog)
    }
}
