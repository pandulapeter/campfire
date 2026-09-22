/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.pandulapeter.campfire.presentation.ui.navigation

import com.pandulapeter.campfire.data.model.domain.LibraryFiles
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.screens.settings.SettingsTab
import kotlin.js.ExperimentalWasmJsInterop

/**
 * The addresses of the web build, relative to the folder the page is served from and still percent-encoded. Every
 * entry of the browser's history the app writes is one of these:
 *
 * - `` — the songs, and `search` over them while their search is open;
 * - `setlists`, and `setlists/search`;
 * - `settings/general`, `settings/songs`, `settings/library`, `settings/about` — one per tab, and always directly
 *   on top of the songs, since picking a tab replaces the entry rather than adding one (`settings` alone opens the tab
 *   that was open last);
 * - `song/{song}`, and `song/{song}/edit` for its editor;
 * - `setlist/{setlist}/{song}` — a song read from a setlist, which follows the pager from song to song.
 *
 * A song is named by its file name without the `.cho` every song the app writes ends in, and a setlist without its
 * `.setlist.json`; a song file with one of the other extensions keeps it, since the name has to find the file again.
 */
internal object BrowserRoutes {

    /**
     * The path of every history entry the app should have, in order, from the songs at the bottom to the screen on
     * top: one per step a back gesture would take, which is a screen of the back stack or an open search. A pure
     * function of states, so that it can be observed.
     */
    fun paths(viewModel: CampfireViewModel) = buildList {
        viewModel.backStack.forEach { destination ->
            when (destination) {
                CampfireDestination.Songs -> {
                    add(ROOT)
                    if (viewModel.songsSearch.isOpen.value) add(SEARCH)
                }

                CampfireDestination.Setlists -> {
                    add(SETLISTS)
                    if (viewModel.setlistsSearch.isOpen.value) add("$SETLISTS/$SEARCH")
                }

                CampfireDestination.Settings -> add("$SETTINGS/${viewModel.settingsTab.pathSegment}")
                is CampfireDestination.SongDetails -> {
                    val song = viewModel.currentSongFileName(destination)?.let(::songPathSegment).orEmpty()
                    add(destination.setlistFileName?.let { "$SETLIST/${setlistPathSegment(it)}/$song" } ?: "$SONG/$song")
                }

                is CampfireDestination.SongEditor -> add("$SONG/${songPathSegment(destination.fileName)}/$EDIT")
            }
        }
    }

    /**
     * The place [path] names in the given library, or null for a path that is not one of the addresses above, or one
     * naming a song or a setlist the library does not hold. Only the things a path names are looked up: whatever it
     * leaves open - the tab of the settings screen, for a bare `settings` - is taken from [current].
     */
    fun resolve(
        path: String,
        songs: List<Song>,
        setlists: List<Setlist>,
        current: NavigationState,
    ): NavigationState? {
        val segments = path.split('/').filter { it.isNotEmpty() }.map { decodePathSegment(it) ?: return null }
        val songFileNames = songs.mapTo(mutableSetOf()) { it.fileName }
        val home = current.copy(
            backStack = listOf(CampfireDestination.Songs),
            isSongsSearchOpen = false,
            isSetlistsSearchOpen = false,
        )
        return when (segments.size) {
            0 -> home
            1 -> when (segments[0]) {
                SEARCH -> home.copy(isSongsSearchOpen = true)
                SETLISTS -> home.copy(backStack = home.backStack + CampfireDestination.Setlists)
                SETTINGS -> home.copy(backStack = home.backStack + CampfireDestination.Settings)
                else -> null
            }

            2 -> when (segments[0]) {
                SETLISTS -> home.copy(backStack = home.backStack + CampfireDestination.Setlists, isSetlistsSearchOpen = true).takeIf { segments[1] == SEARCH }
                SETTINGS -> SettingsTab.entries.firstOrNull { it.pathSegment == segments[1] }?.let { tab ->
                    home.copy(backStack = home.backStack + CampfireDestination.Settings, settingsTab = tab)
                }

                SONG -> songFileName(segments[1], songFileNames)?.let { fileName ->
                    home.copy(backStack = home.backStack + CampfireDestination.SongDetails(songFileNames = listOf(fileName), setlistFileName = null, initialIndex = 0))
                }

                else -> null
            }

            3 -> when (segments[0]) {
                SONG -> songFileName(segments[1], songFileNames)?.takeIf { segments[2] == EDIT }?.let { fileName ->
                    home.copy(
                        backStack = home.backStack + listOf(
                            CampfireDestination.SongDetails(songFileNames = listOf(fileName), setlistFileName = null, initialIndex = 0),
                            CampfireDestination.SongEditor(fileName = fileName),
                        ),
                    )
                }

                SETLIST -> {
                    val setlist = candidates(segments[1], LibraryFiles.SETLIST_EXTENSION).firstNotNullOfOrNull { name -> setlists.firstOrNull { it.fileName == name } }
                    // The songs the pager of a setlist pages through are the ones that can be opened, as when the
                    // setlist's row is tapped (SetlistWithSongs.songs).
                    val pages = setlist?.entries?.map { it.songFileName }?.filter { it in songFileNames }.orEmpty()
                    val index = songFileName(segments[2], pages.toSet())?.let(pages::indexOf) ?: -1
                    if (setlist == null || index < 0) null else home.copy(
                        backStack = home.backStack + listOf(
                            CampfireDestination.Setlists,
                            CampfireDestination.SongDetails(songFileNames = pages, setlistFileName = setlist.fileName, initialIndex = index),
                        ),
                    )
                }

                else -> null
            }

            else -> null
        }
    }

    /**
     * [state] with its back stack cut short at the first screen that names a song or a setlist the library no longer
     * holds, which is what a history entry the browser returns to may do: the library changes while the entry waits.
     * A details screen stays as long as any of its songs does, since it pages through the ones that are there.
     */
    fun validate(
        state: NavigationState,
        songs: List<Song>,
        setlists: List<Setlist>,
    ): NavigationState {
        val songFileNames = songs.mapTo(mutableSetOf()) { it.fileName }
        val setlistFileNames = setlists.mapTo(mutableSetOf()) { it.fileName }
        return state.copy(
            backStack = state.backStack.takeWhile { destination ->
                when (destination) {
                    is CampfireDestination.SongDetails -> destination.songFileNames.any { it in songFileNames } &&
                            (destination.setlistFileName == null || destination.setlistFileName in setlistFileNames)

                    is CampfireDestination.SongEditor -> destination.fileName in songFileNames
                    else -> true
                }
            },
        )
    }

    private fun songPathSegment(fileName: String) = encodePathSegment(fileName.removeSuffix(LibraryFiles.SONG_EXTENSION))

    private fun setlistPathSegment(fileName: String) = encodePathSegment(fileName.removeSuffix(LibraryFiles.SETLIST_EXTENSION))

    private fun songFileName(segment: String, fileNames: Set<String>) = candidates(segment, LibraryFiles.SONG_EXTENSION).firstOrNull { it in fileNames }

    /** The file names a decoded path segment may stand for: the one with the extension taken off it, or itself. */
    private fun candidates(segment: String, extension: String) = listOf(segment + extension, segment)

    private val SettingsTab.pathSegment get() = name.lowercase()

    private const val ROOT = ""
    private const val SEARCH = "search"
    private const val SETLISTS = "setlists"
    private const val SETTINGS = "settings"
    private const val SONG = "song"
    private const val SETLIST = "setlist"
    private const val EDIT = "edit"
}

/**
 * `encodeURIComponent`, and a tilde too, which it leaves alone: GitHub Pages hands a deep address to the app through
 * its 404 page, which carries `&` in the query as `~and~`, and a name that held those five characters would come
 * back as an ampersand.
 */
private fun encodePathSegment(value: String): String = js("encodeURIComponent(value).replace(/~/g, '%7E')")

/** Null for a segment that is not valid percent-encoding, which is an address the app did not write. */
private fun decodePathSegment(value: String): String? = js(
    """(function () {
        try {
            return decodeURIComponent(value);
        } catch (error) {
            return null;
        }
    })()"""
)
