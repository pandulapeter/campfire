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

import com.pandulapeter.campfire.data.formats.zip.ZipEntry
import com.pandulapeter.campfire.data.formats.zip.ZipWriter
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.importing.IncomingContent
import com.pandulapeter.campfire.data.model.importing.StagingRefusedException
import com.pandulapeter.campfire.data.source.local.api.LibraryStorageException
import com.pandulapeter.campfire.data.source.local.implementation.staging.LimitedFileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.JvmFileStorage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** What only the streaming unpack does: caps across nested archives, and nothing left behind when it ends early. */
class StreamingArchiveUnpackTest {

    private val root: File = Files.createTempDirectory("campfire-streaming-unpack").toFile()

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `nested archives whose records together pass the entry cap leave the one past it unread`() = runBlocking {
        val unpacks = ArchiveUnpacks(JvmFileStorage(root))
        // A zip without ZIP64 holds at most 65,534 records, so the cap is passed by siblings: what one request reads.
        val outerFolders = 10_000
        val fullFolders = 65_000
        suspend fun inner(folders: Int, song: String) = ZipWriter.write(folders(folders) + ZipEntry(song, "{title: B}".encodeToByteArray()))
        suspend fun archive(lastFolders: Int) = ZipWriter.write(
            folders(outerFolders) + listOf(
                ZipEntry("a.cho", "{title: A}".encodeToByteArray()),
                ZipEntry("one.zip", inner(fullFolders, "b1.cho")),
                ZipEntry("two.zip", inner(fullFolders, "b2.cho")),
                ZipEntry("three.zip", inner(lastFolders, "b3.cho")),
            ),
        )
        val fitting = ImportLimits.MAX_ARCHIVE_ENTRIES - (outerFolders + 4) - 2 * (fullFolders + 1) - 1

        assertEquals(listOf("a.cho", "b1.cho", "b2.cho", "b3.cho"), unpacks.streamed(archive(fitting)).files.map { it.name })
        val past = unpacks.streamed(archive(fitting + 1)).files
        assertEquals(listOf("a.cho", "b1.cho", "b2.cho", "three.zip"), past.map { it.name })
        assertEquals(ImportedFile.unread("three.zip"), past.last())
        assertEquals(emptyList(), unpacks.stagedNames())
    }

    @Test
    fun `nested archives whose directories together pass the directory cap leave the nested one unread`() = runBlocking {
        val unpacks = ArchiveUnpacks(JvmFileStorage(root))
        val nameLength = 60_000
        suspend fun archive(innerFolders: Int) = ZipWriter.write(
            longFolders(300, nameLength, seed = 2) + listOf(
                ZipEntry("a.cho", "{title: A}".encodeToByteArray()),
                ZipEntry("inner.zip", ZipWriter.write(longFolders(innerFolders, nameLength, seed = 1) + ZipEntry("b.cho", "{title: B}".encodeToByteArray()))),
            ),
        )
        // Each directory alone is far within the cap, which both together pass with the larger nested one.
        assertTrue(500L * nameLength < ImportLimits.MAX_CENTRAL_DIRECTORY_SIZE && 570L * nameLength > ImportLimits.MAX_CENTRAL_DIRECTORY_SIZE)

        assertEquals(listOf("a.cho", "b.cho"), unpacks.streamed(archive(200)).files.map { it.name })
        assertEquals(listOf("a.cho", "inner.zip"), unpacks.streamed(archive(270)).files.map { it.name })
        assertEquals(emptyList(), unpacks.stagedNames())
    }

    @Test
    fun `a storage failure in the middle of the walk leaves nothing of the unpack staged`() = runBlocking {
        val storage = LimitedFileStorage(capacity = 1L shl 30)
        val unpacks = ArchiveUnpacks(storage)
        val staged = unpacks.stage(picturesArchive())
        var writes = 0
        storage.beforeWrite = { if (++writes == 4) throw LibraryStorageException("The disk went away.") }

        assertFailsWith<LibraryStorageException> { unpacks.archiveLocalSource.unpack(archive = staged, maxSize = ImportLimits.MAX_IMPORT_SIZE) }

        assertEquals(listOf(staged.name), storage.stagedNames())
    }

    @Test
    fun `a cancellation in the middle of the walk leaves nothing of the unpack staged`() = runBlocking {
        val storage = LimitedFileStorage(capacity = 1L shl 30)
        val unpacks = ArchiveUnpacks(storage)
        val staged = unpacks.stage(picturesArchive())
        val reached = CompletableDeferred<Unit>()
        var writes = 0
        storage.beforeWrite = {
            if (++writes == 4) {
                reached.complete(Unit)
                awaitCancellation()
            }
        }

        val unpack = async { unpacks.archiveLocalSource.unpack(archive = staged, maxSize = ImportLimits.MAX_IMPORT_SIZE) }
        reached.await()
        unpack.cancelAndJoin()

        assertEquals(listOf(staged.name), storage.stagedNames())
    }

    @Test
    fun `pictures that do not fit the disk are refused before any is staged`() = runBlocking {
        val archive = picturesArchive()
        // Room for the archive and what is kept free beside it, not for the pictures as well.
        val storage = LimitedFileStorage(capacity = archive.size * 5L / 4 + 60_000)
        val unpacks = ArchiveUnpacks(storage)
        val staged = unpacks.stage(archive)
        var writes = 0
        storage.beforeWrite = { writes++ }

        assertFailsWith<StagingRefusedException.NoSpace> { unpacks.archiveLocalSource.unpack(archive = staged, maxSize = ImportLimits.MAX_IMPORT_SIZE) }

        assertEquals(0, writes)
        assertEquals(listOf(staged.name), storage.stagedNames())
    }

    @Test
    fun `a nested archive is staged and unpacked with its pictures and then deleted`() = runBlocking {
        val storage = LimitedFileStorage(capacity = 1L shl 30)
        val unpacks = ArchiveUnpacks(storage)
        val staged = unpacks.stage(ZipWriter.write(listOf(ZipEntry("nested/inner.zip", picturesArchive()))))
        val names = mutableListOf<String>()
        storage.beforeWrite = { names += storage.stagedNames() }

        val unpacked = unpacks.archiveLocalSource.unpack(archive = staged, maxSize = ImportLimits.MAX_IMPORT_SIZE)

        assertTrue(names.any { it.endsWith(".zip") && it != staged.name }, "The nested archive was never staged: $names")
        val pictures = unpacked.files.single().images.map { (it.content as IncomingContent.Staged).file.name }
        assertEquals((pictures + staged.name).sorted(), storage.stagedNames())
    }

    @Test
    fun `room reserved for a picture the walk leaves unread is given back when the unpack ends`() = runBlocking {
        val storage = LimitedFileStorage(capacity = 1L shl 30)
        val unpacks = ArchiveUnpacks(storage)
        val staged = unpacks.stage(withUnknownMethod(picturesArchive(), entryName = "two.png"))
        val request = unpacks.staging.requestOf(staged)

        val unpacked = unpacks.archiveLocalSource.unpack(archive = staged, maxSize = ImportLimits.MAX_IMPORT_SIZE)

        val picture = unpacked.files.single().images.mapNotNull { (it.content as? IncomingContent.Staged)?.file }.single()
        assertEquals(staged.size + picture.size, unpacks.staging.ledger.reservedBy(request.id))
        assertEquals(0, unpacks.staging.ledger.outstanding())
    }

    @Test
    fun `room reserved for pictures a failed walk never reached is given back`() = runBlocking {
        val storage = LimitedFileStorage(capacity = 1L shl 30)
        val unpacks = ArchiveUnpacks(storage)
        val staged = unpacks.stage(picturesArchive())
        val request = unpacks.staging.requestOf(staged)
        var writes = 0
        storage.beforeWrite = { if (++writes == 2) throw LibraryStorageException("The disk went away.") }

        assertFailsWith<LibraryStorageException> { unpacks.archiveLocalSource.unpack(archive = staged, maxSize = ImportLimits.MAX_IMPORT_SIZE) }

        assertEquals(staged.size, unpacks.staging.ledger.reservedBy(request.id))
        assertEquals(0, unpacks.staging.ledger.outstanding())
    }

    /**
     * [archive] with the entry called [entryName] marked as compressed by a method no reader knows, in its local and its
     * central header, so that a walk leaves it out before opening a sink for it.
     */
    private fun withUnknownMethod(archive: ByteArray, entryName: String): ByteArray {
        val bytes = archive.copyOf()
        fun u16(at: Int) = (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8)
        fun u32(at: Int) = u16(at) or (u16(at + 2) shl 16)
        val name = entryName.encodeToByteArray()
        var at = 0
        while (at < bytes.size - 4) {
            if (u32(at) == CENTRAL_HEADER_SIGNATURE && bytes.copyOfRange(at + 46, at + 46 + u16(at + 28)).contentEquals(name)) {
                bytes[at + 10] = UNKNOWN_METHOD
                bytes[u32(at + 42) + 8] = UNKNOWN_METHOD
                return bytes
            }
            at++
        }
        error("No entry called \"$entryName\".")
    }

    /** A song naming two pictures of 100 kB each, which take several writes to stage. */
    private suspend fun picturesArchive() = ZipWriter.write(
        listOf(
            ZipEntry("a.cho", "{title: A}\n{image: src=one.png}\n{image: src=two.png}\n".encodeToByteArray()),
            ZipEntry("one.png", Random(1).nextBytes(100_000)),
            ZipEntry("two.png", Random(2).nextBytes(100_000)),
        ),
    )

    private fun folders(count: Int) = List(count) { ZipEntry("f$it/", ByteArray(0)) }

    @OptIn(ExperimentalStdlibApi::class)
    private fun longFolders(count: Int, nameLength: Int, seed: Int): List<ZipEntry> {
        val random = Random(seed)
        return List(count) { ZipEntry(random.nextBytes(nameLength / 2).toHexString() + "/", ByteArray(0)) }
    }

    private companion object {
        const val CENTRAL_HEADER_SIGNATURE = 0x02014b50
        const val UNKNOWN_METHOD: Byte = 99
    }
}
