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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * What a save hands back is what the caller caches, so it has to be what the file holds: a model built in memory that
 * names a song twice must come back naming it once, the way the document is written. And a save through the edit
 * dialog moves the file only when the title changed.
 */
class SetlistLocalSourceTest {

    private val root: File = Files.createTempDirectory("campfire-setlist").toFile()
    private val fileStorage = JvmFileStorage(root)
    private val setlistLocalSource = SetlistLocalSourceImpl(fileStorage, Logger.Standard)

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

    @Test
    fun `a setlist file that names no day is listed with today's and left as it is`() = runBlocking {
        val undated = """{"title":"Old"}"""
        fileStorage.writeText(StorageDirectory.SETLISTS, "old.setlist.json", undated)
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

        val listed = setlistLocalSource.loadSetlists().single()

        assertEquals(today, listed.setlist.date)
        assertFalse(listed.isDated)
        assertEquals(undated, fileStorage.readText(StorageDirectory.SETLISTS, "old.setlist.json"))
    }

    @Test
    fun `a setlist file that names no day is given today's when it is read on its own and keeps it`() = runBlocking {
        fileStorage.writeText(StorageDirectory.SETLISTS, "old.setlist.json", """{"title":"Old"}""")
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

        assertEquals(today, setlistLocalSource.loadSetlist("old.setlist.json")?.date)
        assertTrue("\"date\": \"$today\"" in fileStorage.readText(StorageDirectory.SETLISTS, "old.setlist.json").orEmpty())
        assertTrue(setlistLocalSource.loadSetlists().single().isDated)
    }

    @Test
    fun `a setlist document that names no day is parsed with today's and says so`() = runBlocking {
        val undated = assertNotNull(setlistLocalSource.parseSetlist("""{"title":"Old"}"""))
        val dated = assertNotNull(setlistLocalSource.parseSetlist("""{"title":"New","date":"2026-09-28"}"""))

        assertEquals(Clock.System.todayIn(TimeZone.currentSystemDefault()), undated.setlist.date)
        assertFalse(undated.isDated)
        assertEquals(LocalDate(2026, 9, 28), dated.setlist.date)
        assertTrue(dated.isDated)
    }

    private fun setlistNamingASongTwice(fileName: String, title: String) = Setlist(
        fileName = fileName,
        title = title,
        description = "",
        date = LocalDate(2026, 1, 1),
        isArchived = false,
        entries = listOf(
            Setlist.Entry(songFileName = "a.cho", transposition = 2),
            Setlist.Entry(songFileName = "b.cho"),
            Setlist.Entry(songFileName = "a.cho", transposition = -1),
        ),
        size = 0L,
    )
}
