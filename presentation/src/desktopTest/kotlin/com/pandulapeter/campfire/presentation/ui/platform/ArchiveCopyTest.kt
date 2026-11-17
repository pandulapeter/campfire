/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.platform

import com.pandulapeter.campfire.data.model.OutOfSpaceException
import com.pandulapeter.campfire.data.model.importing.ReceivedArchive
import com.pandulapeter.campfire.data.model.importing.StagedFile
import com.pandulapeter.campfire.data.model.importing.StagingRefusedException
import com.pandulapeter.campfire.data.model.isOutOfSpace
import com.pandulapeter.campfire.domain.api.importing.DeleteImportStagingUseCase
import com.pandulapeter.campfire.domain.api.importing.ReceiveImportArchiveUseCase
import com.pandulapeter.campfire.presentation.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The copy Android and the desktop receive an archive with ([copyInto], [receiveStream]) through the [ArchiveReceiver]'s
 * job, over a staging that keeps what it is written in memory, as the real one keeps it on disk: whatever ends a copy but
 * its end leaves nothing staged, and the stream is closed however it ends. Real threads (the copy is blocking I/O), so
 * every wait is bounded.
 */
class ArchiveCopyTest {

    private val staged = mutableMapOf<String, ByteArray>()
    private val deleted = mutableListOf<StagedFile>()
    private var room = Long.MAX_VALUE

    private val receiver = ArchiveReceiver(
        scope = ApplicationScope(CoroutineScope(SupervisorJob() + Dispatchers.Default)),
        receiveImportArchive = object : ReceiveImportArchiveUseCase {
            override suspend fun invoke(announcedSize: Long?): ReceivedArchive {
                if (announcedSize != null && announcedSize > room) throw StagingRefusedException.NoSpace(announcedSize)
                return MemoryArchive("session-${staged.size + deleted.size + 1}-1.zip")
            }
        },
        deleteImportStaging = object : DeleteImportStagingUseCase {
            override suspend fun invoke(files: Collection<StagedFile>) {
                deleted += files
                files.forEach { staged -= it.name }
            }
        },
    )

    private inner class MemoryArchive(private val name: String) : ReceivedArchive {

        private var isFinished = false

        init {
            synchronized(staged) { staged[name] = ByteArray(0) }
        }

        override suspend fun write(bytes: ByteArray, offset: Int, length: Int) = synchronized(staged) {
            val written = staged.getValue(name) + bytes.copyOfRange(offset, offset + length)
            if (written.size > room) {
                staged -= name
                throw IOException("No space left on device", OutOfSpaceException())
            }
            staged[name] = written
        }

        override suspend fun take(file: Any, size: Long, onProgress: (Long) -> Unit) = throw UnsupportedOperationException()

        override suspend fun finish(): StagedFile {
            isFinished = true
            return StagedFile(name = name, size = staged.getValue(name).size.toLong(), sha256Hex = null)
        }

        override suspend fun abandon() {
            if (!isFinished) synchronized(staged) { staged -= name }
        }
    }

    /** A stream that says when it was closed. */
    private class TrackedStream(private val input: InputStream) : InputStream() {
        var isClosed = false

        override fun read() = input.read()

        override fun read(b: ByteArray, off: Int, len: Int) = input.read(b, off, len)

        override fun close() {
            isClosed = true
            input.close()
        }
    }

    private fun bytes(size: Int) = Random(size).nextBytes(size)

    @Test
    fun `an archive is copied whole after its head and the stream closed`() = runBlocking<Unit> {
        val content = bytes(700 * 1024)
        val input = TrackedStream(ByteArrayInputStream(content, 512, content.size - 512))

        val file = receiver.receiveStream(name = "library.zip", size = content.size.toLong(), head = content.copyOf(512), input = input)
        val archive = withTimeout(TIMEOUT) { assertNotNull(file.receiving).await() }

        assertContentEquals(content, staged[archive.name])
        assertEquals(content.size.toLong(), archive.size)
        assertEquals(content.size.toLong(), file.receiving!!.receivedBytes.value)
        assertTrue(input.isClosed)
    }

    @Test
    fun `a copy shorter than its announced size fails with nothing staged`() = runBlocking<Unit> {
        val content = bytes(100_000)
        val input = TrackedStream(ByteArrayInputStream(content, 0, 60_000))

        val file = receiver.receiveStream(name = "library.zip", size = content.size.toLong(), head = ByteArray(0), input = input)

        assertFailsWith<IOException> { withTimeout(TIMEOUT) { file.receiving!!.await() } }
        assertTrue(staged.isEmpty())
        assertTrue(input.isClosed)
    }

    @Test
    fun `a copy of no announced size is taken as long as it is`() = runBlocking<Unit> {
        val content = bytes(100_000)

        val file = receiver.receiveStream(name = "library.zip", size = null, head = ByteArray(0), input = ByteArrayInputStream(content))

        assertContentEquals(content, staged[withTimeout(TIMEOUT) { file.receiving!!.await() }.name])
    }

    @Test
    fun `a disk filling up during the copy fails as out of space with nothing staged`() = runBlocking<Unit> {
        room = 300_000
        val input = TrackedStream(ByteArrayInputStream(bytes(1_000_000)))

        val file = receiver.receiveStream(name = "library.zip", size = null, head = ByteArray(0), input = input)

        val failure = assertFailsWith<IOException> { withTimeout(TIMEOUT) { file.receiving!!.await() } }
        assertTrue(failure.isOutOfSpace())
        assertTrue(staged.isEmpty())
        assertTrue(input.isClosed)
    }

    @Test
    fun `an archive announced larger than the room is refused before anything is copied`() = runBlocking<Unit> {
        room = 1000
        val input = TrackedStream(ByteArrayInputStream(bytes(10)))

        val file = receiver.receiveStream(name = "library.zip", size = 2000, head = ByteArray(0), input = input)

        assertFailsWith<StagingRefusedException.NoSpace> { withTimeout(TIMEOUT) { file.receiving!!.await() } }
        assertTrue(staged.isEmpty())
        assertTrue(input.isClosed)
    }

    /**
     * A pipe whose writer stalls after [head]: a read then blocks until the stream is closed, as Android's file streams
     * wake a thread blocked on their descriptor when it is closed.
     */
    private class StalledPipe(private val head: ByteArray) : InputStream() {
        private val closed = CountDownLatch(1)
        private var position = 0

        override fun read() = throw UnsupportedOperationException()

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (position < head.size) {
                val count = minOf(len, head.size - position)
                head.copyInto(b, off, position, position + count)
                position += count
                return count
            }
            closed.await()
            throw IOException("The stream was closed.")
        }

        override fun close() = closed.countDown()
    }

    @Test
    fun `discarding a copy midway on a stalled stream ends it with nothing staged`() = runBlocking<Unit> {
        val input = TrackedStream(StalledPipe(bytes(1000)))

        val file = receiver.receiveStream(name = "library.zip", size = null, head = ByteArray(0), input = input)
        withTimeout(TIMEOUT) { while (file.receiving!!.receivedBytes.value < 1000) Thread.sleep(5) }
        withTimeout(TIMEOUT) { file.receiving!!.discard() }

        assertTrue(input.isClosed)
        assertTrue(staged.isEmpty())
        assertFailsWith<IllegalStateException> { file.receiving!!.await() }
    }

    @Test
    fun `discarding a finished copy deletes its archive`() = runBlocking<Unit> {
        val file = receiver.receiveStream(name = "library.zip", size = null, head = ByteArray(0), input = ByteArrayInputStream(bytes(10)))
        val archive = withTimeout(TIMEOUT) { file.receiving!!.await() }

        file.receiving!!.discard()

        assertEquals(listOf(archive), deleted)
        assertTrue(staged.isEmpty())
    }

    @Test
    fun `the desktop intake stages an archive and reads a song whole`() = runBlocking<Unit> {
        val folder = Files.createTempDirectory("campfire-intake").toFile().apply { deleteOnExit() }
        val archiveBytes = byteArrayOf(0x50, 0x4B, 3, 4) + bytes(5000)
        val archive = folder.resolve("backup.json").apply { writeBytes(archiveBytes) }
        val song = folder.resolve("a.cho").apply { writeText("{title: A}") }

        val files = listOf(archive.absolutePath, song.absolutePath).readAsImportedFiles(receiver = receiver)

        assertContentEquals(archiveBytes, staged[withTimeout(TIMEOUT) { assertNotNull(files[0].receiving).await() }.name])
        assertNull(files[1].receiving)
        assertContentEquals("{title: A}".encodeToByteArray(), files[1].bytes)
        assertTrue(archive.delete(), "The copy left the file open.")
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
