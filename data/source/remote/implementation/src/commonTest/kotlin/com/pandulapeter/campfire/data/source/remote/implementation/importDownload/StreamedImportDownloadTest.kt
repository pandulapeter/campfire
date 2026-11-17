/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.implementation.importDownload

import com.pandulapeter.campfire.data.model.importing.ImportDownload
import com.pandulapeter.campfire.data.source.remote.implementation.network.HttpClientHolder
import com.pandulapeter.campfire.data.source.remote.implementation.network.configureClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StreamedImportDownloadTest {

    @Test
    fun `a body streamed in chunks reaches the sink whole and in order - with its progress against the declared length`() = runTest {
        val content = Random(1).nextBytes(3 * CHUNK + 123)
        val sink = RecordingSink()
        val download = source { _ ->
            respond(chunked(content, pauseMillis = 1_000), HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, content.size.toString()))
        }.stream(sink)

        assertEquals(0, assertIs<ImportDownload.File>(download).bytes.size)
        assertContentEquals(content, sink.bytes())
        assertEquals(listOf<Long?>(content.size.toLong()), sink.announcedSizes)
        assertEquals(content.size.toLong() to content.size.toLong(), sink.progress.last())
        assertEquals(0L to content.size.toLong(), sink.progress.first())
    }

    @Test
    fun `a body without a declared length reports its bytes with no total`() = runTest {
        val content = Random(2).nextBytes(3 * CHUNK)
        val sink = RecordingSink()
        val download = source { _ -> respond(chunked(content), HttpStatusCode.OK) }.stream(sink)

        assertIs<ImportDownload.File>(download)
        assertContentEquals(content, sink.bytes())
        assertEquals(listOf<Long?>(null), sink.announcedSizes)
        assertTrue(sink.progress.all { it.second == null })
        assertEquals(content.size.toLong(), sink.progress.last().first)
    }

    @Test
    fun `a body cut short of its declared length is unreachable`() = runTest {
        val sink = RecordingSink()
        val download = source { _ ->
            respond(chunked(ByteArray(2 * CHUNK)), HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, (4 * CHUNK).toString()))
        }.stream(sink)

        assertEquals(ImportDownload.Unreachable, download)
    }

    @Test
    fun `a body that breaks off with an error is unreachable - even without a declared length`() = runTest {
        val sink = RecordingSink()
        val download = source { _ ->
            val channel = ByteChannel()
            backgroundScope.launch {
                channel.writeFully(ByteArray(CHUNK))
                channel.flush()
                delay(1_000)
                channel.close(IllegalStateException("Connection reset"))
            }
            respond(channel, HttpStatusCode.OK)
        }.stream(sink)

        assertEquals(ImportDownload.Unreachable, download)
    }

    @Test
    fun `a body larger than its declared length is read on within the ceiling as a decoded body is`() = runTest {
        val content = Random(3).nextBytes(3 * CHUNK)
        val sink = RecordingSink()
        val download = source { _ ->
            respond(chunked(content), HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, CHUNK.toString()))
        }.stream(sink)

        assertIs<ImportDownload.File>(download)
        assertContentEquals(content, sink.bytes())
        assertEquals(listOf<Long?>(CHUNK.toLong()), sink.announcedSizes)
    }

    @Test
    fun `a body larger than its declared length and the ceiling is too large`() = runTest {
        val sink = RecordingSink()
        val download = source { _ ->
            respond(chunked(ByteArray(MAX.toInt() + CHUNK)), HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, CHUNK.toString()))
        }.stream(sink)

        assertEquals(ImportDownload.TooLarge, download)
        assertTrue(sink.bytes().size <= MAX)
    }

    @Test
    fun `a body over the ceiling is too large - declared before the body or found in it`() = runTest {
        val declaredSink = RecordingSink()
        val declared = source { _ ->
            respond(chunked(ByteArray(CHUNK)), HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, (MAX + 1).toString()))
        }.stream(declaredSink)
        val foundSink = RecordingSink()
        val found = source { _ -> respond(chunked(ByteArray(MAX.toInt() + 1)), HttpStatusCode.OK) }.stream(foundSink)

        assertEquals(ImportDownload.TooLarge, declared)
        assertTrue(declaredSink.announcedSizes.isEmpty())
        assertEquals(ImportDownload.TooLarge, found)
        assertTrue(foundSink.bytes().size <= MAX)
    }

    @Test
    fun `nothing at the address opens no sink`() = runTest {
        val sink = RecordingSink()

        assertEquals(ImportDownload.Missing, source { _ -> respondError(HttpStatusCode.NotFound) }.stream(sink))
        assertEquals(ImportDownload.Missing, source { _ -> respond(ByteArray(0), HttpStatusCode.OK) }.stream(sink))
        assertEquals(ImportDownload.Unreachable, source { _ -> respondError(HttpStatusCode.BadGateway) }.stream(sink))
        assertTrue(sink.announcedSizes.isEmpty())
    }

    @Test
    fun `a download cancelled midway is cancelled not answered`() = runTest {
        val firstChunk = CompletableDeferred<Unit>()
        val sink = RecordingSink(onWrite = { firstChunk.complete(Unit) })
        val source = source { _ ->
            val channel = ByteChannel()
            backgroundScope.launch {
                channel.writeFully(ByteArray(CHUNK))
                channel.flush()
                awaitCancellation()
            }
            respond(channel, HttpStatusCode.OK)
        }
        val download = async { source.stream(sink) }

        firstChunk.await()
        download.cancel()

        assertFailsWith<CancellationException> { download.await() }
    }

    @Test
    fun `a body that stalls is unreachable once nothing arrived for a minute`() = runTest {
        val sink = RecordingSink()
        val download = source { _ ->
            val channel = ByteChannel()
            backgroundScope.launch {
                channel.writeFully(ByteArray(CHUNK))
                channel.flush()
                awaitCancellation()
            }
            respond(channel, HttpStatusCode.OK)
        }.stream(sink)

        assertEquals(ImportDownload.Unreachable, download)
        assertTrue(currentTime in STALL_LIMIT..<2 * STALL_LIMIT, "Ended after $currentTime ms.")
    }

    @Test
    fun `an answer that never comes is unreachable once a minute passed`() = runTest {
        val download = source { _ -> awaitCancellation() }.stream(RecordingSink())

        assertEquals(ImportDownload.Unreachable, download)
        assertTrue(currentTime in STALL_LIMIT..<2 * STALL_LIMIT, "Ended after $currentTime ms.")
    }

    @Test
    fun `a slow body that keeps arriving has no total time limit not even the browser's`() = runTest {
        val content = Random(4).nextBytes(30 * 1024)
        val sink = RecordingSink()
        val download = source { _ -> respond(chunked(content, chunkSize = 1024, pauseMillis = 50_000), HttpStatusCode.OK) }.stream(sink)

        assertIs<ImportDownload.File>(download)
        assertContentEquals(content, sink.bytes())
        assertTrue(currentTime > 20 * 60_000L)
    }

    @Test
    fun `what the sink throws is thrown on not taken for the network`() = runTest {
        val refusal = IllegalStateException("No room")
        val source = source { _ -> respond(chunked(ByteArray(2 * CHUNK)), HttpStatusCode.OK) }

        assertSame(refusal, assertFailsWith<IllegalStateException> { source.stream(RecordingSink(onWrite = { throw refusal })) })
        assertSame(refusal, assertFailsWith<IllegalStateException> { source.stream(RecordingSink(onOpen = { throw refusal })) })
    }

    /** A body handed over [chunkSize] bytes at a time, [pauseMillis] of virtual time apart. */
    private fun TestScope.chunked(content: ByteArray, chunkSize: Int = CHUNK, pauseMillis: Long = 0) = ByteChannel().also { channel ->
        backgroundScope.launch {
            var offset = 0
            while (offset < content.size) {
                val length = minOf(chunkSize, content.size - offset)
                channel.writeFully(content, offset, offset + length)
                channel.flush()
                offset += length
                delay(pauseMillis)
            }
            channel.flushAndClose()
        }
    }

    private suspend fun ImportDownloadRemoteSourceImpl.stream(sink: RecordingSink) = download(
        url = URL,
        maxSizeBytes = MAX,
        openSink = sink::open,
        onProgress = { received, total -> sink.progress += received to total },
    )

    private class RecordingSink(
        private val onOpen: () -> Unit = {},
        private val onWrite: () -> Unit = {},
    ) {
        val announcedSizes = mutableListOf<Long?>()
        val progress = mutableListOf<Pair<Long, Long?>>()
        private val chunks = mutableListOf<ByteArray>()

        fun open(announcedSize: Long?): suspend (ByteArray, Int, Int) -> Unit {
            onOpen()
            announcedSizes += announcedSize
            return { bytes, offset, length ->
                onWrite()
                chunks += bytes.copyOfRange(offset, offset + length)
            }
        }

        fun bytes() = chunks.fold(ByteArray(0)) { all, chunk -> all + chunk }
    }

    /**
     * Built on the test's scheduler, engine included, so that the stall limit is measured in virtual time, and configured
     * as in the browser, whose total bound on a call the streamed download must lift.
     */
    private fun TestScope.source(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) = ImportDownloadRemoteSourceImpl(
        httpClientHolder = HttpClientHolder(dispatcher = StandardTestDispatcher(testScheduler)) {
            HttpClient(MockEngine.create {
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler(handler)
            }) { configureClient(sendsUserAgent = false, hasSocketTimeout = false) }
        },
    )

    private companion object {
        const val URL = "https://dl.dropboxusercontent.com/scl/fi/abc/share.zip?rlkey=def&dl=1"
        const val CHUNK = 16 * 1024
        const val MAX = 64L * 1024
        const val STALL_LIMIT = ImportDownloadRemoteSourceImpl.STALL_LIMIT_MILLIS
    }
}
