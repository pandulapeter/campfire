/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.widget

import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.ui.playing.testPreferences
import com.pandulapeter.campfire.data.model.setlist.Setlist
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.domain.api.library.ScreenData
import com.pandulapeter.campfire.presentation.ui.navigation.AppLinks
import com.pandulapeter.campfire.presentation.ui.navigation.BrowserRoutes
import com.pandulapeter.campfire.presentation.ui.navigation.FeatureSwitches
import com.pandulapeter.campfire.presentation.ui.navigation.NavigationState
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.navigation.SettingsTab
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class WidgetSnapshotBuilderTest {

    private fun song(fileName: String, title: String) = Song(
        fileName = fileName,
        title = title,
        artist = "Artist",
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

    private val a = song("a.cho", "A")
    private val b = song("ünnep és más.cho", "B")

    private fun setlist(fileName: String, vararg songs: String) = Setlist(
        fileName = fileName,
        title = fileName,
        description = "",
        date = LocalDate(2026, 12, 1),
        isArchived = false,
        entries = songs.map { Setlist.Entry(songFileName = it) },
        size = 0,
    )

    private val library = ScreenData(
        setlists = listOf(setlist("gig.setlist.json", "gone.cho", b.fileName, a.fileName), setlist("empty.setlist.json")),
        songs = listOf(a),
        songSections = emptyList(),
        tags = emptyList(),
        languages = emptyList(),
        unfilteredSongs = listOf(a, b),
        sortedSongs = listOf(b, a),
    )

    private val strings = WidgetTestStrings.strings

    private fun build(preferences: UserPreferences? = null) = widgetSnapshot(
        screenData = library,
        features = FeatureSwitches.of(preferences),
        strings = strings,
        formatDate = { "on $it" },
    )

    private val home = NavigationState(backStack = listOf(CampfireDestination.Songs), isSongsSearchOpen = false, isSetlistsSearchOpen = false, settingsTab = SettingsTab.HOME)

    private fun open(link: String) = shown(BrowserRoutes.resolve(
        path = AppLinks.pathOf(link)!!,
        songs = library.unfilteredSongs,
        setlists = library.setlists,
        current = home,
        isPerformanceModeEnabled = false,
    )?.backStack)

    /** The screens of [backStack] as what they show, without the ids every new one gets. */
    private fun shown(backStack: List<CampfireDestination>?) = backStack?.map { destination ->
        if (destination is CampfireDestination.SongDetails) "${destination.setlistFileName}:${destination.songFileNames}@${destination.initialIndex}" else destination
    }

    @Test
    fun `every song is in the snapshot in the songs screen's order whatever the filters hide`() =
        assertEquals(listOf("B", "A"), build().songs.map { it.title })

    @Test
    fun `a song's link opens its details screen`() = build().songs.forEach { song ->
        assertEquals(
            shown(listOf(CampfireDestination.Songs, CampfireDestination.SongDetails(songFileNames = listOf(song.fileName), setlistFileName = null, initialIndex = 0))),
            open(song.link),
        )
    }

    @Test
    fun `a setlist keeps the songs the library holds and opens the first of them in setlist mode`() {
        val gig = build().setlists.first()
        assertEquals(listOf(b.fileName, a.fileName), gig.songFileNames)
        assertEquals("2026-12-01", gig.date)
        assertEquals("on 2026-12-01", gig.formattedDate)
        assertEquals(
            shown(
                listOf(
                    CampfireDestination.Songs,
                    CampfireDestination.Setlists,
                    CampfireDestination.SongDetails(songFileNames = listOf(b.fileName, a.fileName), setlistFileName = gig.fileName, initialIndex = 0),
                ),
            ),
            open(gig.link),
        )
    }

    @Test
    fun `a setlist with no songs opens the setlists screen with it brought into sight`() {
        val empty = build().setlists.last()
        assertEquals(listOf<Any>(CampfireDestination.Songs, CampfireDestination.Setlists), open(empty.link))
        assertEquals(empty.fileName, BrowserRoutes.setlistToRevealOf(AppLinks.pathOf(empty.link)!!, library.setlists))
    }

    @Test
    fun `the feature switches travel with the snapshot`() {
        val defaults = testPreferences()
        assertFalse(build(defaults.copy(areSongsEnabled = false)).areSongsEnabled)
        assertFalse(build(defaults.copy(areSongsEnabled = false)).areSetlistsEnabled)
        assertFalse(build(defaults.copy(areSetlistsEnabled = false)).areSetlistsEnabled)
    }

    @Test
    fun `an unchanged library builds an equal snapshot - not written again`() = assertEquals(build(), build())
}
