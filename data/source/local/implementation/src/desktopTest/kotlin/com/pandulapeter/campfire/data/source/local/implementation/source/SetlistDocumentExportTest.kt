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

import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.JvmFileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The setlist an export puts in its zip: the stored file, unchanged. */
class SetlistDocumentExportTest {

    private val root: File = Files.createTempDirectory("campfire-setlist-export").toFile()
    private val fileStorage = JvmFileStorage(root)
    private val setlistLocalSource = SetlistLocalSourceImpl(fileStorage, Logger.Standard)

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `the whole setlist is handed out exactly as it is stored`() = runBlocking {
        fileStorage.writeText(StorageDirectory.SETLISTS, FILE_NAME, DOCUMENT)

        assertEquals(DOCUMENT, setlistLocalSource.loadSetlistDocument(FILE_NAME))
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
