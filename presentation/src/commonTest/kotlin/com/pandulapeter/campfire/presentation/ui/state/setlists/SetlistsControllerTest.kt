/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.setlists

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.setlist.Setlist
import com.pandulapeter.campfire.domain.api.library.ScreenData
import com.pandulapeter.campfire.presentation.ui.LowercaseSearchText
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.messages.MessageSink
import com.pandulapeter.campfire.presentation.ui.state.LibraryState
import com.pandulapeter.campfire.presentation.ui.state.SavedStateStore
import com.pandulapeter.campfire.presentation.ui.search.SongSearchSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SetlistsControllerTest {

    private val gig = setlist("gig.setlist.json", "a.cho", "b.cho", "c.cho")
    private val played = setlist("played.setlist.json", "a.cho", "b.cho", isArchived = true)
    private val rehearsal = setlist("rehearsal.setlist.json", "d.cho")
    private val files = FakeSetlistFiles(gig, played, rehearsal)
    private val indexedSongs = MutableStateFlow(
        LibraryState.IndexedSongs(sections = emptyList(), search = SongSearchSnapshot.Empty, filterKey = "", orderKey = "", sorted = emptyList(), isLibraryRead = true),
    )

    @Test
    fun `an archived setlist is not changed by an edit of its songs`() = runTest {
        val controller = controller()
        controller.addSongToSetlist(songFileName = "d.cho", setlistFileName = played.fileName)
        controller.removeSongFromSetlist(songFileName = "a.cho", setlistFileName = played.fileName)
        runCurrent()
        assertEquals(played, files[played.fileName])
    }

    @Test
    fun `a song is added to a setlist once`() = runTest {
        val controller = controller()
        controller.addSongToSetlist(songFileName = "d.cho", setlistFileName = gig.fileName)
        controller.addSongToSetlist(songFileName = "a.cho", setlistFileName = gig.fileName)
        controller.addSongToSetlist(songFileName = "d.cho", setlistFileName = gig.fileName)
        runCurrent()
        assertEquals(listOf("a.cho", "b.cho", "c.cho", "d.cho"), songsOf(gig.fileName))
    }

    @Test
    fun `removing a song takes every entry of it out of the setlist`() = runTest {
        files.invoke(gig.copy(entries = gig.entries + Setlist.Entry(songFileName = "a.cho", transposition = 2)))
        val controller = controller()
        controller.removeSongFromSetlist(songFileName = "a.cho", setlistFileName = gig.fileName)
        runCurrent()
        assertEquals(listOf("b.cho", "c.cho"), songsOf(gig.fileName))
    }

    @Test
    fun `a duplicate copies the setlist's songs as the library has them, not as the sheet saw them`() = runTest {
        val controller = controller()
        val offered = played.copy(entries = listOf(Setlist.Entry(songFileName = "old.cho")))
        controller.duplicateSetlist(offered, title = "Encore", description = "Again", date = LocalDate(2026, 5, 1), isCountdownShown = false)
        runCurrent()
        val copy = files.setlists.value.single { it.title == "Encore" }
        assertEquals(played.entries, copy.entries)
        assertEquals("Again", copy.description)
        assertFalse(copy.isArchived)
        assertEquals(played, files[played.fileName])
    }

    @Test
    fun `a reorder is written where the setlist takes it`() = runTest {
        var isNotWritten = false
        controller().reorderSetlist(gig.fileName, listOf("c.cho", "a.cho", "b.cho")) { isNotWritten = true }
        runCurrent()
        assertEquals(listOf("c.cho", "a.cho", "b.cho"), songsOf(gig.fileName))
        assertFalse(isNotWritten)
    }

    @Test
    fun `a reorder an archived or missing setlist refuses says it was not written`() = runTest {
        val controller = controller()
        var notWritten = 0
        controller.reorderSetlist(played.fileName, listOf("b.cho", "a.cho")) { notWritten++ }
        controller.reorderSetlist("gone.setlist.json", listOf("b.cho", "a.cho")) { notWritten++ }
        runCurrent()
        assertEquals(2, notWritten)
        assertEquals(listOf("a.cho", "b.cho"), songsOf(played.fileName))
    }

    @Test
    fun `the reorder mode ends once its setlist is archived`() = runTest {
        val controller = controller()
        controller.reorderingSetlistFileName = played.fileName
        controller.updateEditableSetlist(played.fileName) { it.copy(entries = emptyList()) }
        assertEquals(null, controller.reorderingSetlistFileName)
        assertTrue(files[played.fileName]?.entries?.isNotEmpty() == true)
    }

    @Test
    fun `the listed setlists carry the query they were narrowed by, not the text of a closed search`() = runTest {
        val controller = controller()
        val search = controller.setlistsSearch
        search.open()
        search.textFieldState.setTextAndPlaceCursorAtEnd("GIG")
        Snapshot.sendApplyNotifications()
        val narrowed = controller.setlistsWithSongs.first { it.filterKey.isNotEmpty() }
        assertEquals("gig", narrowed.filterKey)
        assertEquals(listOf(gig.fileName), narrowed.setlists.map { it.setlist.fileName })
        search.close()
        Snapshot.sendApplyNotifications()
        controller.setlistsWithSongs.first { it.filterKey.isEmpty() }
        search.textFieldState.setTextAndPlaceCursorAtEnd("rehearsal")
        Snapshot.sendApplyNotifications()
        indexedSongs.value = indexedSongs.value.copy(isLibraryRead = false)
        val unnarrowed = controller.setlistsWithSongs.first { !it.isLibraryRead }
        assertEquals("", unnarrowed.filterKey)
        assertEquals(listOf(gig.fileName, rehearsal.fileName), unnarrowed.setlists.map { it.setlist.fileName }.sorted())
    }

    @Test
    fun `the search keeps whether the library has been read`() = runTest {
        indexedSongs.value = indexedSongs.value.copy(isLibraryRead = false)
        val controller = controller()
        controller.setlistsSearch.open()
        controller.setlistsSearch.textFieldState.setTextAndPlaceCursorAtEnd("gig")
        Snapshot.sendApplyNotifications()
        assertFalse(controller.setlistsWithSongs.first { it.filterKey == "gig" }.isLibraryRead)
        indexedSongs.value = indexedSongs.value.copy(isLibraryRead = true)
        val read = controller.setlistsWithSongs.first { it.isLibraryRead }
        assertEquals("gig", read.filterKey)
        assertEquals(listOf(gig.fileName), read.setlists.map { it.setlist.fileName })
    }

    private fun songsOf(fileName: String) = files[fileName]?.entries?.map { it.songFileName }

    private fun setlist(fileName: String, vararg songs: String, isArchived: Boolean = false) = Setlist(
        fileName = fileName,
        title = fileName.removeSuffix(".setlist.json"),
        description = "",
        date = LocalDate(2026, 1, 1),
        isArchived = isArchived,
        entries = songs.map { Setlist.Entry(songFileName = it) },
        size = 0L,
    )

    private fun TestScope.controller() = SetlistsController(
        scope = backgroundScope,
        savedStateStore = SavedStateStore(SavedStateHandle(), backgroundScope),
        backStack = emptyList(),
        userPreferences = MutableStateFlow(null),
        setlists = files.setlists,
        allSongs = MutableStateFlow(emptyList()),
        indexedSongs = indexedSongs,
        screenData = MutableStateFlow<DataState<ScreenData>>(DataState.Loading(null)),
        isImporting = MutableStateFlow(false),
        dialogHost = DialogHost(backgroundScope),
        messageSink = MessageSink(backgroundScope),
        createSetlist = files,
        saveSetlist = files,
        editSetlist = files,
        updateSetlist = files,
        deleteSetlist = files,
        normalizeSearchText = LowercaseSearchText,
        followReportedFileNames = {},
        changeUserPreferences = {},
    )
}
