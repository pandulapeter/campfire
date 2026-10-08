/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.useCases

import com.pandulapeter.campfire.data.model.domain.ImportLimits
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.SongContent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the library export puts into the archive and what it says it left out: an export is the one copy of the library
 * the user keeps elsewhere, and on the web the only one there is.
 */
class ExportLibraryUseCaseImplTest {

    private val archive = FakeArchiveRepository()
    private val songContents = FakeSongContentRepository()

    @Test
    fun `exports nothing when the song scan failed`() = runTest {
        val result = useCase(songs = null, setlists = listOf(setlist("a"), setlist("b"))).invoke()

        assertNull(result)
    }

    @Test
    fun `exports nothing when the setlist scan failed`() = runTest {
        val result = useCase(songs = listOf(song("a")), setlists = null).invoke()

        assertNull(result)
    }

    @Test
    fun `exports nothing for an empty library`() = runTest {
        val result = useCase(songs = emptyList(), setlists = emptyList()).invoke()

        assertNull(result)
    }

    @Test
    fun `names the songs it could not read`() = runTest {
        val result = useCase(
            songs = listOf(song("a"), song("b"), song("c")),
            unreadable = setOf("b.cho"),
        ).invoke()

        assertEquals(listOf("b.cho"), assertNotNull(result).skippedFileNames)
        assertEquals(setOf("songs/a.cho", "songs/c.cho"), archive.packed.keys)
    }

    @Test
    fun `names a song in the folder that is too large to be a song`() = runTest {
        val result = useCase(
            songs = listOf(song("a"), song("b"), song("c")),
            folder = mapOf("a.cho" to 10L, "b.cho" to 10L, "c.cho" to 10L, "huge.cho" to ImportLimits.MAX_TEXT_FILE_SIZE + 1),
        ).invoke()

        assertEquals(listOf("huge.cho"), assertNotNull(result).skippedFileNames)
        assertFalse("huge.cho" in songContents.reads.value)
    }

    @Test
    fun `exports a song in the folder that the scan has not seen yet`() = runTest {
        val result = useCase(
            songs = listOf(song("a"), song("b")),
            folder = mapOf("a.cho" to 10L, "b.cho" to 10L, "new.cho" to 10L),
        ).invoke()

        assertEquals(emptyList(), assertNotNull(result).skippedFileNames)
        assertTrue("songs/new.cho" in archive.packed.keys)
    }

    @Test
    fun `names a song in the folder it could not read`() = runTest {
        val result = useCase(
            songs = listOf(song("a")),
            folder = mapOf("a.cho" to 10L, "b.cho" to 10L),
            unreadable = setOf("b.cho"),
        ).invoke()

        assertEquals(listOf("b.cho"), assertNotNull(result).skippedFileNames)
    }

    @Test
    fun `names a setlist it could not read`() = runTest {
        val result = useCase(
            songs = listOf(song("a")),
            setlists = listOf(setlist("x"), setlist("y")),
            unreadable = setOf("y.setlist.json"),
        ).invoke()

        assertEquals(listOf("y.setlist.json"), assertNotNull(result).skippedFileNames)
        assertEquals(setOf("songs/a.cho", "setlists/x.setlist.json"), archive.packed.keys)
    }

    @Test
    fun `exports a setlist in the folder that the scan could not decode, as it is stored`() = runTest {
        val result = useCase(
            songs = listOf(song("a")),
            setlists = emptyList(),
            setlistFolder = mapOf("broken.setlist.json" to 7L),
            setlistDocument = "{broken",
        ).invoke()

        assertEquals(emptyList(), assertNotNull(result).skippedFileNames)
        assertEquals("{broken", archive.packed.getValue("setlists/broken.setlist.json").decodeToString())
    }

    @Test
    fun `names a setlist in the folder that is too large to be a setlist`() = runTest {
        val result = useCase(
            songs = listOf(song("a")),
            setlistFolder = mapOf("x.setlist.json" to 2L, "huge.setlist.json" to ImportLimits.MAX_TEXT_FILE_SIZE + 1),
        ).invoke()

        assertEquals(listOf("huge.setlist.json"), assertNotNull(result).skippedFileNames)
        assertEquals(setOf("songs/a.cho", "setlists/x.setlist.json"), archive.packed.keys)
    }

    @Test
    fun `keeps the archive layout`() = runTest {
        val result = useCase(songs = listOf(song("a")), setlists = listOf(setlist("x"))).invoke()

        assertEquals(emptyList(), assertNotNull(result).skippedFileNames)
        assertEquals(setOf("songs/a.cho", "setlists/x.setlist.json"), archive.packed.keys)
    }

    @Test
    fun `keeps the sorted order whichever song is read first`() = runTest {
        // Every read waits for the one of the song after it, so the reads can only finish the last song first.
        songContents.readAfter = mapOf("a.cho" to "b.cho", "b.cho" to "c.cho", "c.cho" to "d.cho")

        val result = useCase(songs = listOf("a", "b", "c", "d").map(::song), unreadable = setOf("a.cho", "c.cho")).invoke()

        assertEquals(listOf("d.cho", "c.cho", "b.cho", "a.cho"), songContents.reads.value)
        assertEquals(listOf("a.cho", "c.cho"), assertNotNull(result).skippedFileNames)
        assertEquals(listOf("songs/b.cho", "songs/d.cho"), archive.packed.keys.toList())
    }

    private fun useCase(
        songs: List<Song>?,
        setlists: List<Setlist>? = emptyList(),
        folder: Map<String, Long> = songs.orEmpty().associate { it.fileName to 10L },
        unreadable: Set<String> = emptySet(),
        setlistFolder: Map<String, Long> = setlists.orEmpty().associate { it.fileName to 2L },
        setlistDocument: String = "{}",
    ) = ExportLibraryUseCaseImpl(
        songRepository = object : SongRepositoryStub() {
            override suspend fun loadSongsIfNeeded() = songs
            override suspend fun loadSongFileSizes() = folder
        },
        songContentRepository = songContents.also { it.unreadable = unreadable },
        setlistRepository = object : SetlistRepositoryStub() {
            override suspend fun loadSetlistsIfNeeded() = setlists
            override suspend fun loadSetlistFileSizes() = setlistFolder
            override suspend fun loadSetlistDocument(fileName: String, songFileNames: Set<String>?) = if (fileName in unreadable) null else setlistDocument
        },
        archiveRepository = archive,
    )

    /** Read from several threads at once, since the export reads its songs in parallel on `Dispatchers.Default`. */
    private class FakeSongContentRepository : SongContentRepositoryStub() {
        var unreadable = emptySet<String>()

        /** A read of a key waits until its value has been read, which puts the reads into an order of the test's choosing. */
        var readAfter = emptyMap<String, String>()
        val reads = MutableStateFlow(emptyList<String>())

        override suspend fun loadSongContent(fileName: String, useCache: Boolean): SongContent? {
            readAfter[fileName]?.let { previous -> reads.first { previous in it } }
            reads.update { it + fileName }
            return if (fileName in unreadable) null else SongContent(fileName = fileName, text = "{title: $fileName}")
        }
    }

    private companion object {

        fun song(name: String) = testSong(fileName = "$name.cho")

        fun setlist(name: String) = testSetlist(fileName = "$name.setlist.json")
    }
}
