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

import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrowserRoutesTest {

    private fun song(fileName: String) = Song(
        fileName = fileName,
        title = fileName,
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

    private val songs = listOf(song("a.cho"), song("b.cho"), song("c.chopro"), song("катюша.cho"))

    private val setlist = Setlist(
        fileName = "set.setlist.json",
        title = "Set",
        description = "",
        date = LocalDate(2026, 1, 1),
        isArchived = false,
        entries = listOf(Setlist.Entry(songFileName = "a.cho"), Setlist.Entry(songFileName = "gone.cho"), Setlist.Entry(songFileName = "b.cho")),
        size = 0,
    )

    private val setlists = listOf(setlist)

    private val home = NavigationState(
        backStack = listOf(CampfireDestination.Songs),
        isSongsSearchOpen = false,
        isSetlistsSearchOpen = false,
        settingsTab = SettingsTab.GENERAL,
    )

    private fun songDetails(vararg fileNames: String, setlistFileName: String? = null, index: Int = 0) =
        CampfireDestination.SongDetails(songFileNames = fileNames.toList(), setlistFileName = setlistFileName, initialIndex = index)

    private fun inputs(
        state: NavigationState,
        isSetlistReordering: Boolean = false,
        hasOverlay: Boolean = false,
    ) = RouteInputs(
        backStack = state.backStack,
        isSongsSearchOpen = state.isSongsSearchOpen,
        isSetlistsSearchOpen = state.isSetlistsSearchOpen,
        isSetlistReordering = isSetlistReordering,
        settingsTab = state.settingsTab,
        currentSongFileNames = state.backStack.filterIsInstance<CampfireDestination.SongDetails>()
            .associate { it.id to it.songFileNames.getOrNull(it.initialIndex) },
        hasOverlay = hasOverlay,
    )

    /** The states every address the app writes stands for, with the paths they make. */
    private val statesWithPaths = listOf(
        home to listOf(""),
        home.copy(isSongsSearchOpen = true) to listOf("", "search"),
        home.copy(backStack = listOf(CampfireDestination.Songs, CampfireDestination.Setlists)) to listOf("", "setlists"),
        home.copy(backStack = listOf(CampfireDestination.Songs, CampfireDestination.Setlists), isSetlistsSearchOpen = true) to
            listOf("", "setlists", "setlists/search"),
        home.copy(backStack = listOf(CampfireDestination.Songs, CampfireDestination.Metronome)) to listOf("", "metronome"),
        home.copy(backStack = listOf(CampfireDestination.Songs, CampfireDestination.Tuner)) to listOf("", "tuner"),
        home.copy(backStack = listOf(CampfireDestination.Songs, CampfireDestination.Settings)) to listOf("", "settings/general"),
        home.copy(backStack = listOf(CampfireDestination.Songs, CampfireDestination.Settings), settingsTab = SettingsTab.LIBRARY) to
            listOf("", "settings/general", "settings/library"),
        home.copy(backStack = listOf(CampfireDestination.Songs, songDetails("a.cho"))) to listOf("", "song/a"),
        home.copy(backStack = listOf(CampfireDestination.Songs, songDetails("c.chopro"))) to listOf("", "song/c.chopro"),
        home.copy(backStack = listOf(CampfireDestination.Songs, songDetails("катюша.cho"))) to
            listOf("", "song/%D0%BA%D0%B0%D1%82%D1%8E%D1%88%D0%B0"),
        home.copy(backStack = listOf(CampfireDestination.Songs, songDetails("a.cho"), CampfireDestination.SongEditor(fileName = "a.cho"))) to
            listOf("", "song/a", "song/a/edit"),
        home.copy(
            backStack = listOf(CampfireDestination.Songs, CampfireDestination.Setlists, songDetails("a.cho", "b.cho", setlistFileName = "set.setlist.json", index = 1)),
        ) to listOf("", "setlists", "setlist/set/b"),
    )

    @Test
    fun `every screen, search and settings tab is an entry of its own`() =
        statesWithPaths.forEach { (state, paths) -> assertEquals(paths, BrowserRoutes.paths(inputs(state))) }

    @Test
    fun `the reorder mode repeats the setlists only while their search is closed`() {
        val setlists = home.copy(backStack = listOf(CampfireDestination.Songs, CampfireDestination.Setlists))
        assertEquals(listOf("", "setlists", "setlists"), BrowserRoutes.paths(inputs(setlists, isSetlistReordering = true)))
        assertEquals(
            listOf("", "setlists", "setlists/search"),
            BrowserRoutes.paths(inputs(setlists.copy(isSetlistsSearchOpen = true), isSetlistReordering = true)),
        )
    }

    @Test
    fun `something open over the screen repeats its address`() {
        val state = home.copy(backStack = listOf(CampfireDestination.Songs, songDetails("a.cho")))
        assertEquals(listOf("", "song/a", "song/a"), BrowserRoutes.paths(inputs(state, hasOverlay = true)))
    }

    @Test
    fun `the unsaved changes question is no overlay, any other dialog and a menu are`() {
        assertFalse(RouteInputs.hasOverlay(dialog = DialogType.UnsavedChanges, isAnyMenuOpen = false))
        assertFalse(RouteInputs.hasOverlay(dialog = null, isAnyMenuOpen = false))
        assertTrue(RouteInputs.hasOverlay(dialog = DialogType.ConfirmExit, isAnyMenuOpen = false))
        assertTrue(RouteInputs.hasOverlay(dialog = DialogType.UnsavedChanges, isAnyMenuOpen = true))
    }

    @Test
    fun `the entry count is the number of paths`() =
        statesWithPaths.forEach { (state, paths) -> assertEquals(paths.size, BrowserRoutes.entryCount(state)) }

    @Test
    fun `every address opens the state it was written for`() = statesWithPaths.forEach { (state, paths) ->
        val resolved = BrowserRoutes.resolve(path = paths.last(), songs = songs, setlists = setlists, current = home, isPerformanceModeEnabled = false)
        assertEquals(state.withoutIds(), resolved?.withoutIds(), paths.last())
    }

    @Test
    fun `an editor's address opens only its song in performance mode`() = assertEquals(
        home.copy(backStack = listOf(CampfireDestination.Songs, songDetails("a.cho"))).withoutIds(),
        BrowserRoutes.resolve(path = "song/a/edit", songs = songs, setlists = setlists, current = home, isPerformanceModeEnabled = true)?.withoutIds(),
    )

    @Test
    fun `a bare settings address keeps the tab that was open`() = assertEquals(
        SettingsTab.ABOUT,
        BrowserRoutes.resolve(path = "settings", songs = songs, setlists = setlists, current = home.copy(settingsTab = SettingsTab.ABOUT), isPerformanceModeEnabled = false)?.settingsTab,
    )

    @Test
    fun `an address naming nothing the library holds or written by nobody opens nothing`() = listOf(
        "song/unknown",
        "setlist/set/gone",
        "setlist/unknown/a",
        "settings/nope",
        "song/a/edit/more",
        "song/%C3",
        "nope",
    ).forEach { path ->
        assertNull(BrowserRoutes.resolve(path = path, songs = songs, setlists = setlists, current = home, isPerformanceModeEnabled = false), path)
    }

    @Test
    fun `a stack is cut at the first song the library no longer holds`() = assertEquals(
        listOf(CampfireDestination.Songs),
        BrowserRoutes.validate(
            state = home.copy(backStack = listOf(CampfireDestination.Songs, songDetails("gone.cho"), CampfireDestination.Settings)),
            songs = songs,
            setlists = setlists,
            isPerformanceModeEnabled = false,
            hasImportReport = false,
        ).backStack,
    )

    @Test
    fun `a setlist's song pages through what the setlist holds now`() {
        val stale = songDetails("a.cho", "x.cho", "b.cho", setlistFileName = "set.setlist.json", index = 2)
        val validated = BrowserRoutes.validate(
            state = home.copy(backStack = listOf(CampfireDestination.Songs, CampfireDestination.Setlists, stale)),
            songs = songs,
            setlists = setlists,
            isPerformanceModeEnabled = false,
            hasImportReport = false,
        ).backStack.last() as CampfireDestination.SongDetails
        assertEquals(listOf("a.cho", "b.cho"), validated.songFileNames)
        assertEquals(1, validated.initialIndex)
    }

    @Test
    fun `the editor is cut in performance mode and the import screen without a report`() {
        val state = home.copy(backStack = listOf(CampfireDestination.Songs, songDetails("a.cho"), CampfireDestination.SongEditor(fileName = "a.cho")))
        assertEquals(2, BrowserRoutes.validate(state, songs, setlists, isPerformanceModeEnabled = true, hasImportReport = false).backStack.size)
        assertEquals(3, BrowserRoutes.validate(state, songs, setlists, isPerformanceModeEnabled = false, hasImportReport = false).backStack.size)
        val import = home.copy(backStack = listOf(CampfireDestination.Songs, CampfireDestination.ImportReport))
        assertEquals(1, BrowserRoutes.validate(import, songs, setlists, isPerformanceModeEnabled = false, hasImportReport = false).backStack.size)
        assertEquals(2, BrowserRoutes.validate(import, songs, setlists, isPerformanceModeEnabled = false, hasImportReport = true).backStack.size)
    }

    /** A song details screen is identified by an id made up when it is opened, which no address carries. */
    private fun NavigationState.withoutIds() = copy(
        backStack = backStack.map { if (it is CampfireDestination.SongDetails) it.copy(id = "") else it },
    )
}
