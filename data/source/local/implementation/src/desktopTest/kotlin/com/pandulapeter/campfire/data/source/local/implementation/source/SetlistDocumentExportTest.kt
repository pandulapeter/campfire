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

import com.pandulapeter.campfire.data.source.local.implementation.storage.file.JvmFileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The setlist an export puts in its zip, which leaves out the songs that were left out of the zip and nothing else. */
class SetlistDocumentExportTest {

    private val root: File = Files.createTempDirectory("campfire-setlist-export").toFile()
    private val fileStorage = JvmFileStorage(root)
    private val setlistLocalSource = SetlistLocalSourceImpl(fileStorage)

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `the whole setlist is handed out exactly as it is stored`() = runBlocking {
        fileStorage.writeText(StorageDirectory.SETLISTS, FILE_NAME, DOCUMENT)

        assertEquals(DOCUMENT, setlistLocalSource.loadSetlistDocument(FILE_NAME))
    }

    @Test
    fun `a narrowed setlist keeps the chosen entries in order, their keys and what this version does not know`() = runBlocking {
        fileStorage.writeText(StorageDirectory.SETLISTS, FILE_NAME, DOCUMENT)

        val narrowed = assertNotNull(setlistLocalSource.loadSetlistDocument(FILE_NAME, setOf("c.cho", "a.cho")))
        val setlist = assertNotNull(setlistLocalSource.parseSetlist(narrowed))

        assertEquals(listOf("a.cho" to 2, "c.cho" to -1), setlist.entries.map { it.songFileName to it.transposition })
        assertEquals("Gig", setlist.title)
        assertTrue("\"venue\"" in narrowed)
        assertTrue("\"capoNote\"" in narrowed)
    }

    private companion object {
        const val FILE_NAME = "gig.setlist.json"
        val DOCUMENT = """
            {
                "title": "Gig",
                "songs": [
                    { "file": "a.cho", "transposition": 2, "capoNote": "low" },
                    { "file": "b.cho" },
                    { "file": "c.cho", "transposition": -1 }
                ],
                "venue": "The barn"
            }
        """.trimIndent()
    }
}
