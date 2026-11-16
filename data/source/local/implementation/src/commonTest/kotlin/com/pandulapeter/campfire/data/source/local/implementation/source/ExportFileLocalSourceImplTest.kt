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
import com.pandulapeter.campfire.data.model.library.ExportedContent
import com.pandulapeter.campfire.data.model.library.ExportedFile
import com.pandulapeter.campfire.data.source.local.implementation.InMemoryFileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class ExportFileLocalSourceImplTest {

    private val fileStorage = InMemoryFileStorage()
    private val localSource = ExportFileLocalSourceImpl(fileStorage = fileStorage, logger = Logger.Standard)

    @Test
    fun `what an earlier process left is gone before the first export is written`() = runTest {
        fileStorage.writeBytes(StorageDirectory.EXPORTS, "left.zip", ByteArray(5))

        val writer = localSource.create("songs.zip")
        writer.write(ByteArray(3) { 7 }, 0, 3)
        val content = writer.finish()

        assertEquals(listOf("songs.zip"), fileStorage.listNames(StorageDirectory.EXPORTS))
        assertEquals(ExportedContent.TemporaryFile(path = fileStorage.pathOf(StorageDirectory.EXPORTS, "songs.zip"), size = 3), content)
    }

    @Test
    fun `the folder is emptied once per process and never under an export already written`() = runTest {
        localSource.clear()
        localSource.create("songs.zip").finish()

        localSource.clear()

        assertTrue(fileStorage.exists(StorageDirectory.EXPORTS, "songs.zip"))
    }

    @Test
    fun `a finished export reads back and is removed when discarded`() = runTest {
        val writer = localSource.create("songs.pdf")
        writer.write("%PDF".encodeToByteArray(), 0, 4)
        val file = ExportedFile(name = "songs.pdf", mimeType = ExportedFile.PDF_MIME_TYPE, content = writer.finish())

        assertContentEquals("%PDF".encodeToByteArray(), localSource.read(file))
        localSource.discard(file)

        assertFalse(fileStorage.exists(StorageDirectory.EXPORTS, "songs.pdf"))
    }

    @Test
    fun `an abandoned export leaves nothing behind`() = runTest {
        val writer = localSource.create("songs.zip")
        writer.write(ByteArray(3), 0, 3)

        writer.abandon()

        assertTrue(fileStorage.listNames(StorageDirectory.EXPORTS).isEmpty())
    }
}
