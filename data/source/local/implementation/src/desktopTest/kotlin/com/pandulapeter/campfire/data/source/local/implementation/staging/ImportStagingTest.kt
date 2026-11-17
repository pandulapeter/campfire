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
import com.pandulapeter.campfire.data.model.hashing.Sha256
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.importing.StagingRefusedException
import com.pandulapeter.campfire.data.model.isOutOfSpace
import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.JvmFileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ImportStagingTest {

    private fun bytes(size: Int, seed: Int = 1) = Random(seed).nextBytes(size)

    private suspend fun StagingSink.writeAll(bytes: ByteArray, chunk: Int = CHUNK) {
        var offset = 0
        while (offset < bytes.size) {
            val length = minOf(chunk, bytes.size - offset)
            write(bytes, offset, length)
            offset += length
        }
    }

    @Test
    fun `an unpacked file is named by its session and request and hash`() = runTest {
        val storage = LimitedFileStorage(capacity = 10 * MIB)
        val staging = ImportStaging(storage, Logger.Standard)
        val request = staging.newRequest()
        val content = bytes(3 * CHUNK + 17)

        val staged = staging.openUnpacked(request, ".png", announcedSize = content.size.toLong()).apply { writeAll(content) }.finish()

        assertEquals(Sha256.hashToHex(content), staged.sha256Hex)
        assertEquals(content.size.toLong(), staged.size)
        assertTrue(Regex("${staging.session}-${request.id}-[0-9a-f]{64}\\.png").matches(staged.name), staged.name)
        assertEquals(listOf(staged.name), storage.stagedNames())
        assertContentEquals(content, storage.readBytes(StorageDirectory.IMPORTS, staged.name))
        assertEquals(content.size.toLong(), staging.ledger.reservedBy(request.id))
        assertEquals(0, staging.ledger.outstanding())
    }

    @Test
    fun `a received archive is named by its session and request and number`() = runTest {
        val storage = LimitedFileStorage(capacity = 10 * MIB)
        val staging = ImportStaging(storage, Logger.Standard)
        val request = staging.newRequest()

        val staged = staging.openArchive(request, announcedSize = null).apply { writeAll(bytes(2 * CHUNK)) }.finish()

        assertTrue(Regex("[0-9a-f]{16}-${request.id}-[0-9]+\\.zip").matches(staged.name), staged.name)
        assertTrue(staged.name.startsWith("${staging.session}-"))
        assertEquals(null, staged.sha256Hex)
        assertEquals(2L * CHUNK, staging.ledger.reservedBy(request.id))
    }

    @Test
    fun `the same content unpacked twice is staged once`() = runTest {
        val storage = LimitedFileStorage(capacity = 10 * MIB)
        val staging = ImportStaging(storage, Logger.Standard)
        val request = staging.newRequest()
        val content = bytes(CHUNK)

        val first = staging.openUnpacked(request, ".mp3", announcedSize = null).apply { writeAll(content) }.finish()
        val second = staging.openUnpacked(request, ".mp3", announcedSize = null).apply { writeAll(content) }.finish()

        assertEquals(first, second)
        assertEquals(listOf(first.name), storage.stagedNames())
        assertEquals(CHUNK.toLong(), staging.ledger.reservedBy(request.id))
    }

    @Test
    fun `the same content unpacked again after its staged file vanished is staged anew`() = runTest {
        val storage = LimitedFileStorage(capacity = 10 * MIB)
        val staging = ImportStaging(storage, Logger.Standard)
        val request = staging.newRequest()
        val content = bytes(CHUNK)
        val first = staging.openUnpacked(request, ".mp3", announcedSize = null).apply { writeAll(content) }.finish()
        storage.delete(StorageDirectory.IMPORTS, first.name)

        val second = staging.openUnpacked(request, ".mp3", announcedSize = null).apply { writeAll(content) }.finish()

        assertTrue(staging.isIntact(second))
        assertContentEquals(content, storage.readBytes(StorageDirectory.IMPORTS, second.name))
    }

    @Test
    fun `a file moved out of the staging gives its room back and is found where it went`() = runTest {
        val storage = LimitedFileStorage(capacity = 10 * MIB)
        val staging = ImportStaging(storage, Logger.Standard)
        val request = staging.newRequest()
        val archive = staging.openArchive(request, announcedSize = null).apply { writeAll(bytes(CHUNK, seed = 2)) }.finish()
        val staged = staging.openUnpacked(request, ".pdf", announcedSize = null).apply { writeAll(bytes(CHUNK)) }.finish()

        staging.moveOut(staged, StorageDirectory.FILES, "moved.pdf")

        assertEquals(listOf(archive.name), storage.stagedNames())
        assertEquals(StagedLocation(StorageDirectory.FILES, "moved.pdf"), staging.locate(staged))
        assertTrue(staging.isIntact(staged))
        assertEquals(archive.size, staging.ledger.reservedBy(request.id))
    }

    @Test
    fun `the requests that staged a settled import's files are deleted and no other`() = runTest {
        val storage = LimitedFileStorage(capacity = 10 * MIB)
        val staging = ImportStaging(storage, Logger.Standard)
        val settled = staging.newRequest()
        val other = staging.newRequest()
        val archive = staging.openArchive(settled, announcedSize = null).apply { writeAll(bytes(CHUNK, seed = 2)) }.finish()
        val staged = staging.openUnpacked(settled, ".pdf", announcedSize = null).apply { writeAll(bytes(CHUNK)) }.finish()
        val kept = staging.openUnpacked(other, ".pdf", announcedSize = null).apply { writeAll(bytes(CHUNK, seed = 3)) }.finish()

        staging.deleteRequestsOf(listOf(staged))
        staging.deleteRequestsOf(listOf(staged, archive))

        assertEquals(listOf(kept.name), storage.stagedNames())
        assertEquals(0, staging.ledger.reservedBy(settled.id))
        assertEquals(CHUNK.toLong(), staging.ledger.reservedBy(other.id))
    }

    @Test
    fun `a failed write leaves no partial file`() = runTest {
        val storage = LimitedFileStorage(capacity = 10 * MIB)
        val staging = ImportStaging(storage, Logger.Standard)
        val request = staging.newRequest()
        var writes = 0
        storage.beforeWrite = { if (++writes == 2) throw LibraryStorageException("The disk went away.") }

        assertFailsWith<LibraryStorageException> {
            staging.openUnpacked(request, ".pdf", announcedSize = 3L * CHUNK).writeAll(bytes(3 * CHUNK))
        }

        assertEquals(emptyList(), storage.stagedNames())
        assertEquals(0, staging.ledger.reservedBy(request.id))
    }

    @Test
    fun `a cancelled write leaves no partial file`() = runTest {
        val storage = LimitedFileStorage(capacity = 10 * MIB)
        val staging = ImportStaging(storage, Logger.Standard)
        val request = staging.newRequest()
        var writes = 0
        storage.beforeWrite = { if (++writes == 2) awaitCancellation() }
        val job = launch { staging.openArchive(request, announcedSize = null).writeAll(bytes(3 * CHUNK)) }
        runCurrent()
        assertEquals(1, storage.stagedNames().size)

        job.cancelAndJoin()

        assertEquals(emptyList(), storage.stagedNames())
        assertEquals(0, staging.ledger.reservedBy(request.id))
    }

    @Test
    fun `an abandoned sink leaves no partial file`() = runTest {
        val storage = LimitedFileStorage(capacity = 10 * MIB)
        val staging = ImportStaging(storage, Logger.Standard)
        val request = staging.newRequest()
        val sink = staging.openUnpacked(request, ".jpg", announcedSize = null).apply { writeAll(bytes(CHUNK)) }

        sink.abandon()

        assertEquals(emptyList(), storage.stagedNames())
        assertEquals(0, staging.ledger.reservedBy(request.id))
    }

    @Test
    fun `a write past the budget is refused and leaves no partial file`() = runTest {
        val storage = LimitedFileStorage(capacity = 10 * MIB).apply { reportedSpace = { 100 * KIB - it } }
        val staging = ImportStaging(storage, Logger.Standard)
        val request = staging.newRequest()

        val failure = assertFailsWith<StagingRefusedException.NoSpace> {
            staging.openArchive(request, announcedSize = null).writeAll(bytes(4 * CHUNK))
        }

        assertTrue(failure.isOutOfSpace())
        assertTrue(failure.neededBytes > 100 * KIB)
        assertEquals(emptyList(), storage.stagedNames())
        assertEquals(0, staging.ledger.outstanding())
    }

    @Test
    fun `a full disk fails cleanly with nothing left behind`() = runTest {
        // The storage says it has room it does not have, as Safari's estimate may: the write itself is what fails.
        val storage = LimitedFileStorage(capacity = 100 * KIB).apply { reportedSpace = { Long.MAX_VALUE / 8 } }
        val staging = ImportStaging(storage, Logger.Standard)
        val request = staging.newRequest()

        val failure = assertFailsWith<LibraryStorageException> {
            staging.openUnpacked(request, ".mp4", announcedSize = 4L * CHUNK).writeAll(bytes(4 * CHUNK))
        }

        assertTrue(failure.isOutOfSpace())
        assertEquals(emptyList(), storage.stagedNames())
        assertEquals(0, storage.used())
        assertEquals(0, staging.ledger.outstanding())
    }

    @Test
    fun `an announced size past the budget creates no file`() = runTest {
        val storage = LimitedFileStorage(capacity = 100 * KIB)
        val staging = ImportStaging(storage, Logger.Standard)
        val request = staging.newRequest()

        assertFailsWith<StagingRefusedException.NoSpace> { staging.openArchive(request, announcedSize = 100 * KIB) }
        assertFailsWith<StagingRefusedException.TooLarge> {
            staging.openUnpacked(request, ".png", announcedSize = ImportLimits.MAX_IMPORT_STAGED_SIZE + 1)
        }
        assertFailsWith<StagingRefusedException.TooLarge> {
            staging.openArchive(request, announcedSize = ImportLimits.MAX_STAGED_ARCHIVE_SIZE + 1)
        }

        assertEquals(emptyList(), storage.stagedNames())
        assertEquals(0, staging.ledger.outstanding())
    }

    @Test
    fun `two requests cannot both reserve the same free space`() = runTest {
        val storage = LimitedFileStorage(capacity = 1000 * KIB)
        val staging = ImportStaging(storage, Logger.Standard)
        val first = staging.newRequest()
        val second = staging.newRequest()

        staging.reserve(first, 600 * KIB)
        assertFailsWith<StagingRefusedException.NoSpace> { staging.reserve(second, 600 * KIB) }
        staging.deleteRequest(first)
        staging.reserve(second, 600 * KIB)

        assertEquals(600 * KIB, staging.ledger.outstanding())
    }

    @Test
    fun `two requests reserving at once are measured one after the other`() = runTest {
        val storage = LimitedFileStorage(capacity = 1000 * KIB)
        val staging = ImportStaging(storage, Logger.Standard)
        val requests = listOf(staging.newRequest(), staging.newRequest())

        val outcomes = requests.map { async { runCatching { staging.reserve(it, 600 * KIB) } } }.awaitAll()

        assertEquals(1, outcomes.count { it.isSuccess })
        assertTrue(outcomes.single { it.isFailure }.exceptionOrNull() is StagingRefusedException.NoSpace)
    }

    @Test
    fun `bytes already written are not counted against the free space twice`() = runTest {
        val storage = LimitedFileStorage(capacity = 1000 * KIB)
        val staging = ImportStaging(storage, Logger.Standard)
        val first = staging.newRequest()
        val second = staging.newRequest()
        staging.openArchive(first, announcedSize = 600 * KIB).apply { writeAll(bytes(600 * KIB.toInt())) }.finish()

        // 400 KiB are left: 300 fit beside a margin of a quarter, 400 do not.
        staging.reserve(second, 300 * KIB)
        assertFailsWith<StagingRefusedException.NoSpace> { staging.reserve(second, 100 * KIB) }
    }

    @Test
    fun `a write that grows a request past its reservation shares the ledger with the other`() = runTest {
        val storage = LimitedFileStorage(capacity = 1000 * KIB)
        val staging = ImportStaging(storage, Logger.Standard)
        val first = staging.newRequest()
        val second = staging.newRequest()
        staging.reserve(first, 600 * KIB)

        assertFailsWith<StagingRefusedException.NoSpace> {
            staging.openUnpacked(second, ".png", announcedSize = null).writeAll(bytes(400 * KIB.toInt()))
        }

        assertEquals(emptyList(), storage.stagedNames())
        assertEquals(600 * KIB, staging.ledger.outstanding())
    }

    @Test
    fun `the web keeps its own margin of its quota and charges a file twice its size`() = runTest {
        val storage = LimitedFileStorage(capacity = ImportLimits.MIN_FREE_QUOTA + 2 * MIB, isSpaceAQuota = true)
        val staging = ImportStaging(storage, Logger.Standard)
        val request = staging.newRequest()

        assertFailsWith<StagingRefusedException.NoSpace> { staging.reserve(request, MIB + 1) }
        staging.reserve(request, MIB)
    }

    @Test
    fun `a whole staged file on the web keeps the capacity it may hold reserved until it is deleted`() = runTest {
        val storage = LimitedFileStorage(capacity = 10 * MIB + ImportLimits.MIN_FREE_QUOTA, isSpaceAQuota = true)
        val staging = ImportStaging(storage, Logger.Standard)
        val request = staging.newRequest()
        val file = staging.openArchive(request, announcedSize = MIB).apply { writeAll(bytes(MIB.toInt())) }.finish()

        assertEquals(MIB, staging.ledger.outstanding())
        staging.deleteFile(request, file)
        assertEquals(0, staging.ledger.outstanding())
    }

    @Test
    fun `other sessions' files go at launch and this session's stay`() = runTest {
        val storage = LimitedFileStorage(capacity = 10 * MIB)
        val earlier = ImportStaging(storage, Logger.Standard)
        val earlierFile = earlier.openArchive(earlier.newRequest(), announcedSize = null).apply { writeAll(bytes(CHUNK)) }.finish()
        val earlierPartial = earlier.openUnpacked(earlier.newRequest(), ".png", announcedSize = null).apply { writeAll(bytes(CHUNK)) }
        storage.writeBytes(StorageDirectory.IMPORTS, "left-by-hand.zip", bytes(10))
        val current = ImportStaging(storage, Logger.Standard)
        val currentRequest = current.newRequest()
        val currentFile = current.openUnpacked(currentRequest, ".png", announcedSize = null).apply { writeAll(bytes(CHUNK, seed = 2)) }.finish()
        val currentPartial = current.openArchive(currentRequest, announcedSize = null).apply { writeAll(bytes(CHUNK)) }

        current.deleteOtherSessions()

        assertEquals(listOf(currentFile.name, currentPartial.name).sorted(), storage.stagedNames())
        assertTrue(earlierFile.name !in storage.stagedNames() && earlierPartial.name !in storage.stagedNames())
    }

    @Test
    fun `deleting a request removes its files and no other`() = runTest {
        val storage = LimitedFileStorage(capacity = 10 * MIB)
        val staging = ImportStaging(storage, Logger.Standard)
        val deleted = staging.newRequest()
        val kept = staging.newRequest()
        staging.openArchive(deleted, announcedSize = null).apply { writeAll(bytes(CHUNK)) }.finish()
        val open = staging.openUnpacked(deleted, ".png", announcedSize = null).apply { writeAll(bytes(CHUNK)) }
        val keptFile = staging.openArchive(kept, announcedSize = null).apply { writeAll(bytes(CHUNK)) }.finish()

        staging.deleteRequest(deleted)

        assertEquals(listOf(keptFile.name), storage.stagedNames())
        assertEquals(0, staging.ledger.reservedBy(deleted.id))
        assertFailsWith<IllegalStateException> { open.write(bytes(1), 0, 1) }
        assertFailsWith<IllegalStateException> { staging.openArchive(deleted, announcedSize = null) }
        assertEquals(listOf(keptFile.name), storage.stagedNames())
    }

    @Test
    fun `a staged file deleted on its own gives its room back`() = runTest {
        val storage = LimitedFileStorage(capacity = 10 * MIB)
        val staging = ImportStaging(storage, Logger.Standard)
        val request = staging.newRequest()
        val archive = staging.openArchive(request, announcedSize = null).apply { writeAll(bytes(CHUNK)) }.finish()

        staging.deleteFile(request, archive)

        assertEquals(emptyList(), storage.stagedNames())
        assertEquals(0, staging.ledger.reservedBy(request.id))
    }

    @Test
    fun `staging on the disk renames a finished file and leaves nothing partial`() = runTest {
        val root = Files.createTempDirectory("campfire-staging").toFile()
        try {
            val storage = JvmFileStorage(root)
            val staging = ImportStaging(storage, Logger.Standard)
            val request = staging.newRequest()
            val content = bytes(3 * CHUNK)

            val staged = staging.openUnpacked(request, ".png", announcedSize = null).apply { writeAll(content) }.finish()
            staging.openArchive(request, announcedSize = null).apply { writeAll(content) }.abandon()

            assertEquals(listOf(staged.name), storage.listNames(StorageDirectory.IMPORTS))
            assertContentEquals(content, storage.readBytes(StorageDirectory.IMPORTS, staged.name))
            staging.deleteRequest(request)
            assertEquals(emptyList(), storage.listNames(StorageDirectory.IMPORTS))
        } finally {
            root.deleteRecursively()
        }
    }
}

private const val KIB = 1024L
private const val MIB = 1024L * KIB
private const val CHUNK = 64 * 1024
