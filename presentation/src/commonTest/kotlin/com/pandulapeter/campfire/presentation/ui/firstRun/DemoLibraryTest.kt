/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.firstRun

import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.model.displayTitle
import com.pandulapeter.campfire.data.model.domain.LibraryFiles
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The bundled files as the app reads them. Their names have to be the ones the library would give them, or the demo
 * would be imported next to itself as `_2` copies, and offered again for ever, wherever it is already there.
 */
class DemoLibraryTest {

    @Test
    fun `every demo song is named the way the library names it from its own header`() = runTest {
        val songs = DemoLibrary.read().filter { LibraryFiles.isSongFileName(it.name) }
        assertTrue(songs.isNotEmpty())
        songs.forEach { file ->
            val metadata = ChordProParser.parseMetadata(file.bytes.decodeToString())
            val title = LibraryFiles.normalizedName(metadata.displayTitle(fallback = ""))
            val artist = metadata.artist.orEmpty()
            val expected = if (artist.isBlank()) title else LibraryFiles.normalizedName(artist) + LibraryFiles.NORMALIZED_ARTIST_TITLE_SEPARATOR + title
            assertEquals(expected + LibraryFiles.SONG_EXTENSION, file.name)
        }
    }

    @Test
    fun `the demo setlist is named after its title and names only bundled songs`() = runTest {
        val files = DemoLibrary.read()
        val setlist = files.single { LibraryFiles.isSetlistFileName(it.name) }
        val document = Json.parseToJsonElement(setlist.bytes.decodeToString()).jsonObject
        assertEquals(LibraryFiles.normalizedName(document.getValue("title").jsonPrimitive.content) + LibraryFiles.SETLIST_EXTENSION, setlist.name)
        val entries = document.getValue("songs").jsonArray.map { it.jsonObject.getValue("file").jsonPrimitive.content }
        assertEquals(files.map { it.name }.filter(LibraryFiles::isSongFileName).toSet(), entries.toSet())
    }

    @Test
    fun `the demo is only present with every song and the setlist`() = runTest {
        val files = DemoLibrary.read()
        val songs = files.map { it.name }.filter(LibraryFiles::isSongFileName).map(::song)
        val setlists = files.map { it.name }.filter(LibraryFiles::isSetlistFileName).map(::setlist)
        assertTrue(DemoLibrary.isPresentIn(songs = songs, setlists = setlists))
        assertFalse(DemoLibrary.isPresentIn(songs = songs, setlists = emptyList()))
        assertFalse(DemoLibrary.isPresentIn(songs = songs.drop(1), setlists = setlists))
    }

    private fun song(fileName: String) = Song(
        fileName = fileName, title = fileName, artist = "", key = null, transpose = 0, tags = emptyList(), languages = emptyList(),
        coverArtUrl = null, hasChords = true, canUpdateFileName = false, lastModified = 0, size = 0,
    )

    private fun setlist(fileName: String) = Setlist(
        fileName = fileName,
        title = fileName,
        description = "",
        date = LocalDate(2026, 1, 1),
        isArchived = false,
        entries = emptyList(),
        size = 0,
    )
}
