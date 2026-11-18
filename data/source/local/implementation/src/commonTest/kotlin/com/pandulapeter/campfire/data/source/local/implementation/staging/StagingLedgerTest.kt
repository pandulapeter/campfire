/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.staging

import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.OutOfSpaceException
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.importing.StagingRefusedException
import com.pandulapeter.campfire.data.model.isOutOfSpace
import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException
import com.pandulapeter.campfire.data.source.local.implementation.InMemoryFileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileSink
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StagingLedgerTest {

    @Test
    fun `the web keeps its own margin of its quota and charges a file being written twice its size`() = runTest {
        val staging = ImportStaging(QuotaStorage(capacity = ImportLimits.MIN_FREE_QUOTA + 2 * MIB), Logger.Standard)
        val request = staging.newRequest()

        assertFailsWith<StagingRefusedException.NoSpace> { staging.reserve(request, MIB + 1) }
        staging.reserve(request, MIB)
    }

    @Test
    fun `a closed file on the web holds only its size`() = runTest {
        val storage = QuotaStorage(capacity = ImportLimits.MIN_FREE_QUOTA + 3 * MIB)
        val staging = ImportStaging(storage, Logger.Standard)
        val request = staging.newRequest()

        staging.openArchive(request, announcedSize = MIB).apply { write(ByteArray(MIB.toInt()), 0, MIB.toInt()) }.finish()

        assertEquals(MIB, staging.ledger.reservedBy(request.id))
        assertEquals(0, staging.ledger.outstanding())
        // What is left of the quota (2 MiB) holds one more file of 1 MiB charged twice while it is written.
        staging.openUnpacked(request, ".png", announcedSize = MIB).apply { write(ByteArray(MIB.toInt()) { 1 }, 0, MIB.toInt()) }.finish()
    }

    @Test
    fun `a closed file on the web is given back at its size`() = runTest {
        val staging = ImportStaging(QuotaStorage(capacity = ImportLimits.MIN_FREE_QUOTA + 3 * MIB), Logger.Standard)
        val request = staging.newRequest()
        val kept = staging.openArchive(request, announcedSize = MIB).apply { write(ByteArray(MIB.toInt()), 0, MIB.toInt()) }.finish()
        val deleted = staging.openArchive(request, announcedSize = null).apply { write(ByteArray(KIB.toInt()), 0, KIB.toInt()) }.finish()

        staging.deleteFile(request, deleted)

        assertEquals(kept.size, staging.ledger.reservedBy(request.id))
        assertEquals(0, staging.ledger.outstanding())
    }

    @Test
    fun `a storage that cannot tell its free space refuses nothing up front`() = runTest {
        val staging = ImportStaging(QuotaStorage(capacity = 2 * MIB, reportsSpace = false), Logger.Standard)
        val request = staging.newRequest()

        staging.reserve(request, ImportLimits.MAX_STAGED_ARCHIVE_SIZE)
        staging.reserve(request, ImportLimits.MAX_IMPORT_STAGED_SIZE)
    }

    @Test
    fun `a write the storage refuses without saying its free space fails as a full disk with nothing left behind`() = runTest {
        val storage = QuotaStorage(capacity = MIB, reportsSpace = false)
        val staging = ImportStaging(storage, Logger.Standard)
        val request = staging.newRequest()
        val sink = staging.openArchive(request, announcedSize = 2 * MIB)

        val failure = assertFailsWith<LibraryStorageException> { sink.write(ByteArray(2 * MIB.toInt()), 0, 2 * MIB.toInt()) }

        assertTrue(failure.isOutOfSpace())
        assertEquals(emptyList(), storage.listNames(StorageDirectory.IMPORTS))
        assertEquals(0, staging.ledger.reservedBy(request.id))
    }
}

/**
 * A web origin's storage of [capacity] bytes, its free space reported as the quota left, or not at all where it does not
 * [reportsSpace] (Chrome), refusing a write past it as a browser refuses one past the quota.
 */
private class QuotaStorage(
    private val capacity: Long,
    private val reportsSpace: Boolean = true,
    private val delegate: InMemoryFileStorage = InMemoryFileStorage(),
) : FileStorage by delegate {

    override val isSpaceAQuota = true

    private suspend fun used() = StorageDirectory.entries.sumOf { directory -> delegate.list(directory).sumOf { it.size } }

    override suspend fun availableSpace(directory: StorageDirectory) = if (reportsSpace) capacity - used() else null

    override suspend fun openSink(directory: StorageDirectory, name: String): FileSink {
        val sink = delegate.openSink(directory, name)
        return object : FileSink by sink {
            override suspend fun write(bytes: ByteArray, offset: Int, length: Int) {
                if (used() + length > capacity) throw LibraryStorageException("No room for \"$name\".", OutOfSpaceException())
                sink.write(bytes, offset, length)
            }
        }
    }
}

private const val KIB = 1024L
private const val MIB = 1024L * KIB
