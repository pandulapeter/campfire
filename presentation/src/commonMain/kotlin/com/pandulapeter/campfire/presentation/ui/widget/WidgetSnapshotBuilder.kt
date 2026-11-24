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

import com.pandulapeter.campfire.domain.api.library.ScreenData
import com.pandulapeter.campfire.presentation.ui.navigation.AppLinks
import com.pandulapeter.campfire.presentation.ui.navigation.BrowserRoutes
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.navigation.FeatureSwitches
import kotlinx.datetime.LocalDate

/**
 * The snapshot of [screenData], a library read whole, under the [features] switches: the songs in the songs screen's
 * order with the filters left out, and the setlists in the setlists screen's, each with the songs of it the library
 * holds and the app link a tap opens. [formatDate] writes a setlist's day in the app's language.
 */
internal fun widgetSnapshot(
    screenData: ScreenData,
    features: FeatureSwitches,
    strings: WidgetSnapshot.Strings,
    theme: WidgetSnapshot.Theme,
    formatDate: (LocalDate) -> String,
): WidgetSnapshot {
    val songFileNames = screenData.unfilteredSongs.mapTo(mutableSetOf()) { it.fileName }
    // The sorted list is left empty by a library read before it was built; the songs are still all there, unsorted.
    val songs = screenData.sortedSongs.ifEmpty { screenData.unfilteredSongs }
    return WidgetSnapshot(
        songs = songs.map { song ->
            WidgetSnapshot.Song(
                fileName = song.fileName,
                title = song.title,
                artist = song.artist,
                link = AppLinks.of(BrowserRoutes.songPathOf(song.fileName)),
            )
        },
        setlists = screenData.setlists.map { setlist ->
            // The ones a setlist's pager pages through, as its address opens them (BrowserRoutes.resolve).
            val setlistSongs = setlist.entries.map { it.songFileName }.filter { it in songFileNames }
            WidgetSnapshot.Setlist(
                fileName = setlist.fileName,
                title = setlist.title,
                date = setlist.date.toString(),
                formattedDate = formatDate(setlist.date),
                isArchived = setlist.isArchived,
                songFileNames = setlistSongs,
                link = AppLinks.of(BrowserRoutes.setlistPathOf(setlistFileName = setlist.fileName, songFileName = setlistSongs.firstOrNull())),
                songLinks = setlistSongs.map { AppLinks.of(BrowserRoutes.setlistPathOf(setlistFileName = setlist.fileName, songFileName = it)) },
            )
        },
        areSongsEnabled = features.areSongsEnabled,
        areSetlistsEnabled = features.areSetlistsEnabled,
        homeLink = AppLinks.of(""),
        setlistsLink = AppLinks.of(BrowserRoutes.pathOf(CampfireDestination.Setlists)),
        strings = strings,
        theme = theme,
    )
}
