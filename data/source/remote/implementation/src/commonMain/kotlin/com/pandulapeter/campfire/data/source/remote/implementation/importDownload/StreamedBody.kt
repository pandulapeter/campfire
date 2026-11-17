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
import com.pandulapeter.campfire.data.source.remote.implementation.network.isCutShort
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.yield

/**
 * A failure of the caller's sink, carried past the download's own handling, which reads every other failure as the
 * network's, and thrown on unwrapped by it.
 */
internal class SinkFailure(override val cause: Throwable) : Exception(cause.message, cause)

/**
 * Reads [response]'s body chunk by chunk into the sink [openSink] opens at the first byte, at most [maxSizeBytes] of it,
 * telling [heard] of every chunk for the stall limit. Answers [ImportDownload.File] without bytes once the whole body
 * went to the sink, else why it is not the file; a body that broke off with an error is thrown as that error.
 *
 * A body longer than the declared length is read on, within [maxSizeBytes]: an engine that decoded a compressed body
 * (iOS and the browser, which also hides a cross-origin `Content-Encoding`) hands over more than the length declared, as
 * [isCutShort] explains, and the declared length is only the staging's first reservation.
 */
internal suspend fun HttpResponse.streamBody(
    maxSizeBytes: Long,
    openSink: suspend (announcedSize: Long?) -> suspend (bytes: ByteArray, offset: Int, length: Int) -> Unit,
    onProgress: (receivedBytes: Long, totalBytes: Long?) -> Unit,
    heard: () -> Unit,
    name: () -> String,
): ImportDownload {
    val declared = contentLength()
    onProgress(0, declared)
    val channel = bodyAsChannel()
    val buffer = ByteArray(STREAM_CHUNK_SIZE)
    var sink: (suspend (ByteArray, Int, Int) -> Unit)? = null
    var received = 0L
    while (true) {
        val read = channel.readAvailable(buffer, 0, buffer.size)
        if (read == -1) break
        if (read == 0) continue
        heard()
        received += read
        if (received > maxSizeBytes) return ImportDownload.TooLarge
        val open = sink ?: guarded { openSink(declared) }.also { sink = it }
        guarded { open(buffer, 0, read) }
        onProgress(received, declared)
        // A body already buffered is read without suspending, which would hold the browser's one thread and keep a
        // cancellation waiting for the whole of it.
        yield()
    }
    // A channel closed by a transfer error reads as its end, so the error is asked for rather than taken as a whole file.
    channel.closedCause?.let { throw it }
    return when {
        received == 0L -> ImportDownload.Missing
        isCutShort(received) -> ImportDownload.Unreachable
        else -> ImportDownload.File(name = name(), bytes = ByteArray(0))
    }
}

private inline fun <T> guarded(block: () -> T): T = try {
    block()
} catch (exception: CancellationException) {
    throw exception
} catch (throwable: Throwable) {
    throw SinkFailure(throwable)
}

/** How much of a streamed body is read at a time: the staging writes it as it comes. */
private const val STREAM_CHUNK_SIZE = 64 * 1024
