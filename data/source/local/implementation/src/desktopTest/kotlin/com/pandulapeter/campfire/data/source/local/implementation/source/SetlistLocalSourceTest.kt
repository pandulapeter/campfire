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
 * What a save hands back is what the caller caches, so it has to be what the file holds: a model built in memory that
 * names a song twice must come back naming it once, the way the document is written. And a save through the edit
 * dialog moves the file only when the title changed.
 */
class SetlistLocalSourceTest {

    private val root: File = Files.createTempDirectory("campfire-setlist").toFile()
    private val fileStorage = JvmFileStorage(root)
    private val setlistLocalSource = SetlistLocalSourceImpl(fileStorage)

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `a setlist that names a song twice is saved and handed back naming it once`() = runBlocking {
        val saved = setlistLocalSource.saveSetlist(setlistNamingASongTwice(fileName = "gig.setlist.json", title = "Gig"))

        assertEquals(listOf("a.cho", "b.cho"), saved.entries.map { it.songFileName })
        assertEquals(2, saved.entries.first().transposition)
        assertEquals(listOf("a.cho", "b.cho"), setlistLocalSource.loadSetlist("gig.setlist.json")?.entries?.map { it.songFileName })
    }

    @Test
    fun `a setlist renamed while it names a song twice is handed back naming it once`() = runBlocking {
        val setlist = setlistNamingASongTwice(fileName = "gig.setlist.json", title = "Gig")
        setlistLocalSource.saveSetlist(setlist)

        val renamed = setlistLocalSource.renameSetlist(setlist, "Concert")

        assertEquals("concert.setlist.json", renamed.fileName)
        assertEquals(listOf("a.cho", "b.cho"), renamed.entries.map { it.songFileName })
        assertEquals(2, renamed.entries.first().transposition)
        assertEquals(listOf("a.cho", "b.cho"), setlistLocalSource.loadSetlist("concert.setlist.json")?.entries?.map { it.songFileName })
    }

    @Test
    fun `a setlist saved under its own title keeps a file name of another rule`() = runBlocking {
        val setlist = setlistNamingASongTwice(fileName = "My Set.setlist.json", title = "My Set")
        setlistLocalSource.saveSetlist(setlist)

        val saved = setlistLocalSource.renameSetlist(setlist.copy(description = "For the lake"), "My Set")

        assertEquals("My Set.setlist.json", saved.fileName)
        assertEquals(listOf("My Set.setlist.json"), fileStorage.list(StorageDirectory.SETLISTS).map { it.name })
        assertEquals("For the lake", setlistLocalSource.loadSetlist("My Set.setlist.json")?.description)
    }

    @Test
    fun `a setlist given a new title moves to the name that title gives`() = runBlocking {
        val setlist = setlistNamingASongTwice(fileName = "My Set.setlist.json", title = "My Set")
        setlistLocalSource.saveSetlist(setlist)

        val renamed = setlistLocalSource.renameSetlist(setlist, "Other")

        assertEquals("other.setlist.json", renamed.fileName)
        assertEquals(listOf("other.setlist.json"), fileStorage.list(StorageDirectory.SETLISTS).map { it.name })
    }

    private fun setlistNamingASongTwice(fileName: String, title: String) = Setlist(
        fileName = fileName,
        title = title,
        description = "",
        priority = 0,
        isArchived = false,
        entries = listOf(
            Setlist.Entry(songFileName = "a.cho", transposition = 2),
            Setlist.Entry(songFileName = "b.cho"),
            Setlist.Entry(songFileName = "a.cho", transposition = -1),
        ),
        size = 0L,
    )
}
