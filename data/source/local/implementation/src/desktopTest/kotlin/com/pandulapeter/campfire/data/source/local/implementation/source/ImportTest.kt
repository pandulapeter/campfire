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

import com.pandulapeter.campfire.data.model.domain.Logger
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.JvmFileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * What an import writes once the planner has decided: a replacement goes over the library file, and keeping both
 * leaves the library file exactly as it was and writes the arriving one next to it.
 */
class ImportTest {

    private val root: File = Files.createTempDirectory("campfire-import").toFile()
    private val fileStorage = JvmFileStorage(root)
    private val songLocalSource = SongLocalSourceImpl(fileStorage, Logger.Standard)
    private val setlistLocalSource = SetlistLocalSourceImpl(fileStorage, Logger.Standard)

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `a replacing song import writes over the library file`() = runBlocking {
        fileStorage.writeText(StorageDirectory.SONGS, "a.cho", "{title: A}\nOld\n")

        val song = songLocalSource.importSong(fileName = "a.cho", text = "{title: A}\nNew\n", shouldReplace = true)

        assertEquals("a.cho", song.fileName)
        assertEquals(listOf("a.cho"), fileStorage.list(StorageDirectory.SONGS).map { it.name })
        assertEquals("{title: A}\nNew\n", fileStorage.readText(StorageDirectory.SONGS, "a.cho"))
    }

    @Test
    fun `a song import that keeps both numbers the new file and leaves the library one untouched`() = runBlocking {
        fileStorage.writeText(StorageDirectory.SONGS, "a.cho", "{title: A}\nOld\n")
        val original = songFile("a.cho").readBytes()

        val song = songLocalSource.importSong(fileName = "a.cho", text = "{title: A}\nNew\n", shouldReplace = false)

        assertEquals("a_2.cho", song.fileName)
        assertContentEquals(original, songFile("a.cho").readBytes())
        assertEquals("{title: A}\nNew\n", fileStorage.readText(StorageDirectory.SONGS, "a_2.cho"))
    }

    @Test
    fun `an imported song carries the size its file has on disk`() = runBlocking {
        val text = "{title: Tükörfúrógép}\n[Am]Őszi szél, 千と千尋 ${String(Character.toChars(0x1F3B8))}\n"

        val song = songLocalSource.importSong(fileName = "tukorfurogep.cho", text = text, shouldReplace = false)

        assertEquals(songFile(song.fileName).length(), song.size)
        assertEquals("Tükörfúrógép", song.title)
    }

    @Test
    fun `a replacing setlist import writes over the library file`() = runBlocking {
        setlistLocalSource.saveSetlist(setlist(description = "Old"))

        val imported = setlistLocalSource.importSetlist(setlist(description = "New"), shouldReplace = true)

        assertEquals("summer.setlist.json", imported.fileName)
        assertEquals(listOf("summer.setlist.json"), fileStorage.list(StorageDirectory.SETLISTS).map { it.name })
        assertEquals("New", setlistLocalSource.loadSetlist("summer.setlist.json")?.description)
    }

    @Test
    fun `a setlist import that keeps both numbers the new file and leaves the library one untouched`() = runBlocking {
        setlistLocalSource.saveSetlist(setlist(description = "Old"))
        val original = setlistFile("summer.setlist.json").readBytes()

        val imported = setlistLocalSource.importSetlist(setlist(description = "New"), shouldReplace = false)

        assertEquals("summer_2.setlist.json", imported.fileName)
        assertContentEquals(original, setlistFile("summer.setlist.json").readBytes())
        assertEquals("New", setlistLocalSource.loadSetlist("summer_2.setlist.json")?.description)
        assertEquals(setlistFile("summer_2.setlist.json").length(), imported.size)
    }

    private fun songFile(name: String) = File(root, "library/songs/$name")

    private fun setlistFile(name: String) = File(root, "library/setlists/$name")

    private fun setlist(description: String) = Setlist(
        fileName = "summer.setlist.json",
        title = "Summer",
        description = description,
        date = LocalDate(2026, 1, 1),
        isArchived = false,
        entries = listOf(Setlist.Entry("a.cho")),
        size = 0L,
    )
}
