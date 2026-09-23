/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.source

import com.pandulapeter.campfire.data.model.domain.ImportLimits
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.JvmFileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Which files of a library folder are the library, against a folder that also holds what macOS copies along. */
class LibraryListingTest {

    private val root: File = Files.createTempDirectory("campfire-listing").toFile()
    private val fileStorage = JvmFileStorage(root)

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `a hidden companion of a song is not a song`() = runBlocking {
        writeSongs()

        val songs = SongLocalSourceImpl(fileStorage).loadSongs {}

        assertEquals(listOf("a.cho"), songs.map { it.fileName })
    }

    @Test
    fun `a hidden companion of a setlist is not a setlist`() = runBlocking {
        val setlistLocalSource = SetlistLocalSourceImpl(fileStorage)
        writeSetlists(setlistLocalSource)

        assertEquals(1, setlistLocalSource.loadSetlists().size)
    }

    @Test
    fun `sync does not see hidden files`() = runBlocking {
        writeSongs()
        writeSetlists(SetlistLocalSourceImpl(fileStorage))

        val files = LibraryFileLocalSourceImpl(fileStorage).loadLibraryFiles()

        assertEquals(listOf("a.cho", "summer.setlist.json"), files.map { it.name })
    }

    @Test
    fun `a song file larger than any song is left out of the scan`() = runBlocking {
        writeSongs()
        fileStorage.writeBytes(StorageDirectory.SONGS, "b.cho", tooLarge())

        val songs = SongLocalSourceImpl(fileStorage).loadSongs {}

        assertEquals(listOf("a.cho"), songs.map { it.fileName })
    }

    @Test
    fun `a setlist file larger than any setlist is left out of the scan`() = runBlocking {
        val setlistLocalSource = SetlistLocalSourceImpl(fileStorage)
        writeSetlists(setlistLocalSource)
        fileStorage.writeBytes(StorageDirectory.SETLISTS, "winter.setlist.json", tooLarge())

        assertEquals(listOf("summer.setlist.json"), setlistLocalSource.loadSetlists().map { it.fileName })
    }

    @Test
    fun `a scanned song carries the size of its file`() = runBlocking {
        fileStorage.writeText(StorageDirectory.SONGS, "a.cho", "{title: A}\n")
        fileStorage.writeText(StorageDirectory.SONGS, "b.cho", "{title: Bé}\nLonger text\n")

        val songs = SongLocalSourceImpl(fileStorage).loadSongs {}

        songs.forEach { song -> assertEquals(File(root, "library/songs/${song.fileName}").length(), song.size) }
        assertEquals(2, songs.size)
    }

    @Test
    fun `a saved setlist carries the size of its file`() = runBlocking {
        val setlistLocalSource = SetlistLocalSourceImpl(fileStorage)
        fun sizeOf(fileName: String) = File(root, "library/setlists/$fileName").length()

        val saved = setlistLocalSource.saveSetlist(
            Setlist(
                fileName = "Summer.setlist.json",
                title = "Summer",
                description = "Pécs",
                priority = 0,
                isArchived = false,
                entries = listOf(Setlist.Entry("a.cho")),
                size = 0L,
            ),
        )
        assertEquals(sizeOf(saved.fileName), saved.size)
        // The title changes but the name it gives differs from the file's only in case, so the file is written where it is.
        val retitled = setlistLocalSource.renameSetlist(saved, "SUMMER")
        assertEquals("Summer.setlist.json", retitled.fileName)
        assertEquals(sizeOf(retitled.fileName), retitled.size)
        val moved = setlistLocalSource.renameSetlist(retitled, "Autumn evenings")
        assertEquals("autumn_evenings.setlist.json", moved.fileName)
        assertEquals(sizeOf(moved.fileName), moved.size)

        assertEquals(listOf(sizeOf(moved.fileName)), setlistLocalSource.loadSetlists().map { it.size })
    }

    @Test
    fun `a scan of 1,000 songs publishes after 64, 128, 256 and 512 songs`() = runBlocking {
        (1..1000).forEach { fileStorage.writeText(StorageDirectory.SONGS, "song_$it.cho", "{title: Song $it}\n") }
        val published = mutableListOf<Int>()

        val songs = SongLocalSourceImpl(fileStorage).loadSongs { published += it.size }

        assertEquals(listOf(64, 128, 256, 512), published)
        assertEquals(1000, songs.size)
    }

    private fun tooLarge() = ByteArray((ImportLimits.MAX_TEXT_FILE_SIZE + 1).toInt()) { 'x'.code.toByte() }

    private suspend fun writeSongs() {
        fileStorage.writeText(StorageDirectory.SONGS, "a.cho", "{title: A}\n")
        fileStorage.writeBytes(StorageDirectory.SONGS, "._a.cho", APPLE_DOUBLE)
    }

    private suspend fun writeSetlists(setlistLocalSource: SetlistLocalSourceImpl) {
        setlistLocalSource.saveSetlist(
            Setlist(
                fileName = "summer.setlist.json",
                title = "Summer",
                description = "",
                priority = 0,
                isArchived = false,
                entries = emptyList(),
                size = 0L,
            ),
        )
        fileStorage.writeBytes(StorageDirectory.SETLISTS, "._summer.setlist.json", APPLE_DOUBLE)
    }

    private companion object {
        /** The first bytes of an AppleDouble file, which is all a listing ever needs to not look at. */
        val APPLE_DOUBLE = byteArrayOf(0, 5, 22, 7, 0, 2)
    }
}
