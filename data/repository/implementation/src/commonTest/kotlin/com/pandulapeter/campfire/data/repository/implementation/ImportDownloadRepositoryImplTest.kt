/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.implementation

import com.pandulapeter.campfire.data.model.importing.ImportDownload
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.importing.ReceivedArchive
import com.pandulapeter.campfire.data.model.importing.StagedFile
import com.pandulapeter.campfire.data.model.importing.StagingRefusedException
import com.pandulapeter.campfire.data.source.local.api.ImportStagingLocalSource
import com.pandulapeter.campfire.data.source.remote.api.ImportDownloadRemoteSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImportDownloadRepositoryImplTest {

    private val staging = FakeImportStagingLocalSource()

    @Test
    fun `the download stages a zip as the intake stages archives`() = runTest {
        val remote = ScriptedRemoteSource(chunks = zipChunks(2))

        val download = assertIs<ImportDownload.File>(ImportDownloadRepositoryImpl(remote, staging).download(URL) { _, _ -> })

        assertEquals(setOf(assertIs<StagedFile>(download.staged).name), staging.files.keys)
    }

    @Test
    fun `a zip streamed in chunks is staged whole with its progress passed on`() = runTest {
        val chunks = zipChunks(3)
        val progress = mutableListOf<Pair<Long, Long?>>()

        val download = repository(ScriptedRemoteSource(chunks, announcedSize = 3L * CHUNK))
            .downloadIntoStaging(URL) { received, total -> progress += received to total }

        val file = assertIs<ImportDownload.File>(download)
        val staged = assertIs<StagedFile>(file.staged)
        assertEquals(0, file.bytes.size)
        assertEquals(NAME, file.name)
        assertContentEquals(chunks.reduce(ByteArray::plus), staging.files.getValue(staged.name))
        assertEquals(listOf<Long?>(3L * CHUNK), staging.announcedSizes)
        assertEquals(3L * CHUNK to 3L * CHUNK, progress.last())
    }

    @Test
    fun `a body that is no zip is read into memory as before and nothing is staged`() = runTest {
        val chunks = listOf("{title: Riverlight}\n".encodeToByteArray(), "[G]Words\n".encodeToByteArray())

        val download = assertIs<ImportDownload.File>(repository(ScriptedRemoteSource(chunks)).downloadIntoStaging(URL) { _, _ -> })

        assertNull(download.staged)
        assertContentEquals(chunks.reduce(ByteArray::plus), download.bytes)
        assertTrue(staging.files.isEmpty())
    }

    @Test
    fun `a body too short to be a zip is read into memory`() = runTest {
        val download = assertIs<ImportDownload.File>(
            repository(ScriptedRemoteSource(listOf(byteArrayOf(0x50, 0x4b)))).downloadIntoStaging(URL) { _, _ -> },
        )

        assertContentEquals(byteArrayOf(0x50, 0x4b), download.bytes)
        assertTrue(staging.files.isEmpty())
    }

    @Test
    fun `a body that is no zip and larger than memory takes is too large`() = runTest {
        val megabyte = ByteArray(1 shl 20) { 'a'.code.toByte() }
        val chunks = List((ImportLimits.MAX_ARCHIVE_SIZE / megabyte.size + 1).toInt()) { megabyte }

        val download = repository(ScriptedRemoteSource(chunks)).downloadIntoStaging(URL) { _, _ -> }

        assertEquals(ImportDownload.TooLarge, download)
        assertTrue(staging.files.isEmpty())
    }

    @Test
    fun `a zip download that does not come to the file leaves no staged file`() = runTest {
        // Cut short, larger than the ceiling, stalled, or turned away: whatever the remote answers once the bytes went on.
        listOf(ImportDownload.Unreachable, ImportDownload.TooLarge, ImportDownload.Missing).forEach { answer ->
            val download = repository(ScriptedRemoteSource(zipChunks(2), answer = answer)).downloadIntoStaging(URL) { _, _ -> }

            assertEquals(answer, download)
            assertEquals(1, staging.announcedSizes.size.also { staging.announcedSizes.clear() })
            assertTrue(staging.files.isEmpty(), "$answer left ${staging.files.keys}.")
        }
    }

    @Test
    fun `a zip download cancelled midway leaves no staged file`() = runTest {
        val written = CompletableDeferred<Unit>()
        val remote = ScriptedRemoteSource(zipChunks(2), afterChunks = {
            written.complete(Unit)
            awaitCancellation()
        })
        val download = async { repository(remote).downloadIntoStaging(URL) { _, _ -> } }

        written.await()
        assertEquals(1, staging.files.size)
        download.cancel()

        assertFailsWith<CancellationException> { download.await() }
        assertTrue(staging.files.isEmpty())
    }

    @Test
    fun `a zip the staging has no room for is refused with no staged file`() = runTest {
        val staging = FakeImportStagingLocalSource(room = CHUNK.toLong())

        assertFailsWith<StagingRefusedException.NoSpace> {
            ImportDownloadRepositoryImpl(ScriptedRemoteSource(zipChunks(3)), staging).downloadIntoStaging(URL) { _, _ -> }
        }
        assertTrue(staging.files.isEmpty())
    }

    private fun repository(remote: ScriptedRemoteSource) = ImportDownloadRepositoryImpl(remote, staging)

    /** [count] chunks of a body that starts as a zip does. */
    private fun zipChunks(count: Int) = List(count) { index ->
        Random(index).nextBytes(CHUNK).also { if (index == 0) byteArrayOf(0x50, 0x4b, 3, 4).copyInto(it) }
    }

    /**
     * A remote that hands the sink [chunks], as the real one does (opened at the first byte, with [announcedSize]), runs
     * [afterChunks], and then gives [answer], the file by default.
     */
    private class ScriptedRemoteSource(
        private val chunks: List<ByteArray>,
        private val announcedSize: Long? = null,
        private val answer: ImportDownload = ImportDownload.File(name = NAME, bytes = ByteArray(0)),
        private val afterChunks: suspend () -> Unit = {},
    ) : ImportDownloadRemoteSource {

        override suspend fun download(url: String, maxSizeBytes: Long) = IN_MEMORY

        override suspend fun download(
            url: String,
            maxSizeBytes: Long,
            openSink: suspend (announcedSize: Long?) -> suspend (bytes: ByteArray, offset: Int, length: Int) -> Unit,
            onProgress: (receivedBytes: Long, totalBytes: Long?) -> Unit,
        ): ImportDownload {
            var sink: (suspend (ByteArray, Int, Int) -> Unit)? = null
            var received = 0L
            chunks.forEach { chunk ->
                val open = sink ?: openSink(announcedSize).also { sink = it }
                open(chunk, 0, chunk.size)
                received += chunk.size
                onProgress(received, announcedSize)
            }
            afterChunks()
            return answer
        }

        override suspend fun downloadPicture(url: String, maxSizeBytes: Long): ImportDownload =
            throw NotImplementedError("Not expected to be called by this test.")

        companion object {
            val IN_MEMORY = ImportDownload.File(name = "in-memory.zip", bytes = byteArrayOf(0x50, 0x4b, 3, 4))
        }
    }

    /** Staged files in memory, partial ones included, refusing whatever passes [room] as the real staging does. */
    private class FakeImportStagingLocalSource(private val room: Long = Long.MAX_VALUE) : ImportStagingLocalSource {

        val files = mutableMapOf<String, ByteArray>()
        val announcedSizes = mutableListOf<Long?>()
        private var count = 0

        override suspend fun deleteOtherSessions() = Unit

        override suspend fun isIntact(file: StagedFile): Boolean = throw NotImplementedError("Not expected to be called by this test.")

        override suspend fun deleteRequestsOf(files: Collection<StagedFile>): Unit = throw NotImplementedError("Not expected to be called by this test.")

        override suspend fun receiveArchive(announcedSize: Long?): ReceivedArchive {
            if (announcedSize != null && announcedSize > room) throw StagingRefusedException.NoSpace(announcedSize)
            announcedSizes += announcedSize
            val name = "session-${++count}-1.zip"
            files[name] = ByteArray(0)
            return object : ReceivedArchive {
                private var isFinished = false

                override suspend fun write(bytes: ByteArray, offset: Int, length: Int) {
                    val written = files.getValue(name) + bytes.copyOfRange(offset, offset + length)
                    if (written.size > room) {
                        files -= name
                        throw StagingRefusedException.NoSpace(written.size.toLong())
                    }
                    files[name] = written
                }

                override suspend fun take(file: Any, size: Long, onProgress: (Long) -> Unit) =
                    throw NotImplementedError("Not expected to be called by this test.")

                override suspend fun finish(): StagedFile {
                    isFinished = true
                    return StagedFile(name = name, size = files.getValue(name).size.toLong(), sha256Hex = null)
                }

                override suspend fun abandon() {
                    if (!isFinished) files -= name
                }
            }
        }
    }

    private companion object {
        const val URL = "https://example.com/library.zip"
        const val NAME = "library.zip"
        const val CHUNK = 16 * 1024
    }
}
