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

import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.JvmFileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Renames against the real file system of the machine running the tests, which on macOS and Windows is
 * case-insensitive by default: the one kind of file system where a rename that only changes case can go wrong.
 */
class RenameTest {

    private val root: File = Files.createTempDirectory("campfire-rename").toFile()
    private val fileStorage = JvmFileStorage(root)
    private val songLocalSource = SongLocalSourceImpl(fileStorage)
    private val setlistLocalSource = SetlistLocalSourceImpl(fileStorage)

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `a song renamed only in case keeps its name without a number`() = runBlocking {
        fileStorage.writeText(StorageDirectory.SONGS, "Foo.cho", "{title: Foo}\n")
        val song = songLocalSource.loadSong("Foo.cho")!!

        val renamed = songLocalSource.renameSong(song)

        assertEquals("foo.cho", renamed?.fileName)
        assertEquals(listOf("foo.cho"), fileStorage.list(StorageDirectory.SONGS).map { it.name })
        assertEquals("{title: Foo}\n", fileStorage.readText(StorageDirectory.SONGS, "foo.cho"))
    }

    @Test
    fun `a song renamed to a name another song has is numbered`() = runBlocking {
        fileStorage.writeText(StorageDirectory.SONGS, "bar.cho", "{title: Bar}\n")
        fileStorage.writeText(StorageDirectory.SONGS, "Old name.cho", "{title: Bar}\n{subtitle: Live}\n")
        fileStorage.writeText(StorageDirectory.SONGS, "bar_live.cho", "{title: Something else}\n")
        val song = songLocalSource.loadSong("Old name.cho")!!

        val renamed = songLocalSource.renameSong(song)

        assertEquals("bar_live_2.cho", renamed?.fileName)
        assertEquals(listOf("bar.cho", "bar_live.cho", "bar_live_2.cho"), fileStorage.list(StorageDirectory.SONGS).map { it.name })
    }

    @Test
    fun `a setlist renamed only in case keeps its name without a number`() = runBlocking {
        val setlist = Setlist(
            fileName = "Summer.setlist.json",
            title = "Summer",
            description = "",
            priority = 0,
            isArchived = false,
            entries = emptyList(),
            size = 0L,
        )
        setlistLocalSource.saveSetlist(setlist)

        val renamed = setlistLocalSource.renameSetlist(setlist, "Summer")

        assertEquals("summer.setlist.json", renamed.fileName)
        assertEquals(listOf("summer.setlist.json"), fileStorage.list(StorageDirectory.SETLISTS).map { it.name })
        assertEquals("Summer", setlistLocalSource.loadSetlists().single().title)
    }
}
