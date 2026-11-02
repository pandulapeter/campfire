/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SongDetailsDecisionsTest {

    private val destination = CampfireDestination.SongDetails(songFileNames = listOf("a.cho"), setlistFileName = null, initialIndex = 0)

    @Test
    fun `a song the library lost closes its screen when it is on top`() {
        assertTrue(shouldCloseSongDetails(isImportPreview = false, resolvedSongs = emptyList(), top = destination, destination = destination))
        // Not before the library has been read, nor while the song is still there.
        assertFalse(shouldCloseSongDetails(isImportPreview = false, resolvedSongs = null, top = destination, destination = destination))
        assertFalse(shouldCloseSongDetails(isImportPreview = false, resolvedSongs = listOf(song("a.cho")), top = destination, destination = destination))
    }

    @Test
    fun `an import preview never closes itself`() {
        assertFalse(shouldCloseSongDetails(isImportPreview = true, resolvedSongs = emptyList(), top = destination, destination = destination))
    }

    @Test
    fun `a screen under another one stays open`() {
        assertFalse(shouldCloseSongDetails(isImportPreview = false, resolvedSongs = emptyList(), top = destination.copy(id = "other"), destination = destination))
        assertFalse(shouldCloseSongDetails(isImportPreview = false, resolvedSongs = emptyList(), top = CampfireDestination.Songs, destination = destination))
    }

    @Test
    fun `a song is uncovered only with no dialog over it`() {
        assertTrue(isSongDetailsUncovered(visibleDialog = null, top = destination))
        assertFalse(isSongDetailsUncovered(visibleDialog = DialogType.NewSong, top = destination))
        assertFalse(isSongDetailsUncovered(visibleDialog = null, top = CampfireDestination.Songs))
    }

    private fun song(fileName: String) = Song(
        fileName = fileName, title = fileName, artist = "", key = null, transpose = 0, tags = emptyList(), languages = emptyList(),
        coverArtUrl = null, hasChords = true, canUpdateFileName = false, lastModified = 0, size = 0,
    )
}
