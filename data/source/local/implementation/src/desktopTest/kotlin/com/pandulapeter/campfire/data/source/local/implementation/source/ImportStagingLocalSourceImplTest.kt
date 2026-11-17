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
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.importing.ReceivedArchive
import com.pandulapeter.campfire.data.model.importing.StagingRefusedException
import com.pandulapeter.campfire.data.source.local.api.ImportStagingLocalSource
import com.pandulapeter.campfire.data.source.local.implementation.staging.ImportStaging
import com.pandulapeter.campfire.data.source.local.implementation.staging.LimitedFileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImportStagingLocalSourceImplTest {

    private val storage = LimitedFileStorage(capacity = 4 * MIB)
    private val staging = ImportStaging(storage, Logger.Standard)
    private val source = ImportStagingLocalSourceImpl(staging)

    @Test
    fun `a received archive is staged whole, unhashed, and stays once finished`() = runTest {
        val content = Random(1).nextBytes(3 * CHUNK + 5)
        val received = source.receiveArchive(announcedSize = content.size.toLong())
        received.writeInChunks(content)

        val staged = received.finish()
        received.abandon()

        assertEquals(content.size.toLong(), staged.size)
        assertNull(staged.sha256Hex)
        assertEquals(listOf(staged.name), storage.stagedNames())
        assertContentEquals(content, storage.readBytes(StorageDirectory.IMPORTS, staged.name))
    }

    @Test
    fun `an abandoned archive leaves no file and no room reserved`() = runTest {
        val received = source.receiveArchive(announcedSize = 8 * CHUNK.toLong())
        received.writeInChunks(ByteArray(2 * CHUNK))

        received.abandon()

        assertEquals(emptyList(), storage.stagedNames())
        assertEquals(0, staging.ledger.outstanding())
    }

    @Test
    fun `an archive that outgrows the room fails with no file left and no room reserved`() = runTest {
        val received = source.receiveArchive(announcedSize = null)

        assertFailsWith<StagingRefusedException.NoSpace> { received.writeInChunks(ByteArray(8 * MIB.toInt())) }

        assertEquals(emptyList(), storage.stagedNames())
        assertEquals(0, staging.ledger.outstanding())
    }

    @Test
    fun `an archive announced past what may be staged or past the room is refused before a file is made`() = runTest {
        assertFailsWith<StagingRefusedException.TooLarge> { source.receiveArchive(announcedSize = ImportLimits.MAX_STAGED_ARCHIVE_SIZE + 1) }
        assertFailsWith<StagingRefusedException.NoSpace> { source.receiveArchive(announcedSize = 8 * MIB) }

        assertTrue(storage.stagedNames().isEmpty())
        assertEquals(0, staging.ledger.outstanding())
    }

    private suspend fun ReceivedArchive.writeInChunks(content: ByteArray) {
        var offset = 0
        while (offset < content.size) {
            val length = minOf(CHUNK, content.size - offset)
            write(content, offset, length)
            offset += length
        }
    }
}

private const val MIB = 1024L * 1024
private const val CHUNK = 64 * 1024
