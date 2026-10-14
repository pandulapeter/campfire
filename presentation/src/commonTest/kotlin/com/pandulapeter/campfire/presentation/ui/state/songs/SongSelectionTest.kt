/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.songs

import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.domain.api.models.ScreenData
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SongSelectionTest {

    private val library = listOf(song("a"), song("b"))

    @Test
    fun `a ticked song missing from a whole library is unticked`() = runTest {
        val selection = selectionOf("a", "gone")
        val screenData = MutableStateFlow(screenDataOf(library))
        selection.startPruning(screenData)
        runCurrent()

        assertEquals(setOf("a.cho"), selection.selectedSongFileNames)
    }

    @Test
    fun `an emission of the same library under another filter keeps the selection`() = runTest {
        val selection = selectionOf("a", "b")
        val screenData = MutableStateFlow(screenDataOf(library))
        selection.startPruning(screenData)
        runCurrent()
        screenData.value = screenDataOf(library, songs = listOf(library.first()))
        runCurrent()

        assertEquals(setOf("a.cho", "b.cho"), selection.selectedSongFileNames)
    }

    @Test
    fun `a song ticked between two emissions of the same library is still checked`() = runTest {
        val selection = selectionOf("a")
        val screenData = MutableStateFlow(screenDataOf(library))
        selection.startPruning(screenData)
        runCurrent()
        selection.toggle("gone.cho")
        screenData.value = screenDataOf(library, songs = listOf(library.first()))
        runCurrent()

        assertEquals(setOf("a.cho"), selection.selectedSongFileNames)
    }

    @Test
    fun `a library not read whole prunes nothing`() = runTest {
        val selection = selectionOf("a", "gone")
        val screenData = MutableStateFlow(screenDataOf(library, isWholeLibrary = false))
        selection.startPruning(screenData)
        runCurrent()

        assertEquals(setOf("a.cho", "gone.cho"), selection.selectedSongFileNames)
    }

    @Test
    fun `the selection is listed in the order the songs are sorted in, whatever order it was ticked in`() {
        val sorted = listOf(song("c"), song("a"), song("b"))

        assertEquals(listOf("c.cho", "a.cho"), setOf("a.cho", "c.cho").inListOrder(sorted))
    }

    @Test
    fun `a ticked song the sorted list does not hold comes after the listed ones`() {
        val sorted = listOf(song("b"), song("a"))

        assertEquals(listOf("b.cho", "a.cho", "hidden.cho"), setOf("hidden.cho", "a.cho", "b.cho").inListOrder(sorted))
    }

    private fun TestScope.selectionOf(vararg names: String) = SongSelection(scope = backgroundScope, backStack = listOf(CampfireDestination.Songs)).apply {
        start(names.first() + ".cho")
        names.drop(1).forEach { toggle("$it.cho") }
    }

    private fun screenDataOf(
        unfilteredSongs: List<Song>,
        songs: List<Song> = unfilteredSongs,
        isWholeLibrary: Boolean = true,
    ): DataState<ScreenData> = DataState.Idle(
        ScreenData(
            setlists = emptyList(),
            songs = songs,
            songSections = emptyList(),
            tags = emptyList(),
            languages = emptyList(),
            unfilteredSongs = unfilteredSongs,
            isWholeLibrary = isWholeLibrary,
        ),
    )

    private fun song(name: String) = Song(
        fileName = "$name.cho",
        title = name,
        artist = "",
        key = null,
        transpose = 0,
        tags = emptyList(),
        languages = emptyList(),
        coverArtUrl = null,
        hasChords = true,
        canUpdateFileName = false,
        lastModified = 0,
        size = 0,
    )
}
