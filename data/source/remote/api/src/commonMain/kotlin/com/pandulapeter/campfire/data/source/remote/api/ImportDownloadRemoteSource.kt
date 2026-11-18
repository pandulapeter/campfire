/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.api

import com.pandulapeter.campfire.data.model.importing.ImportDownload

/**
 * Downloads a file the user asked to import from an address. The address may point at any host, and its answer is
 * trusted no further than its size: an implementation follows redirects, never reads more than [download]'s ceiling
 * allows, does not judge the file by the content type the host claims (a share link's is whatever the host says), and
 * keeps nothing once it has answered.
 */
interface ImportDownloadRemoteSource {

    /**
     * The file at [url], or why there is none, never read past [maxSizeBytes] and never held in memory: the body is
     * handed to a sink chunk by chunk as it arrives. [openSink] is called at most once, with the length the host declared
     * (null where it sent none), just before the first byte is written, and never for an answer that is not the file; a
     * [ImportDownload.File] answer carries no bytes, its name only, since they all went to the sink. Any other answer, or
     * a cancellation, means that what the sink was given is not the file and is the caller's to throw away.
     *
     * No time limit applies to the whole download, which may take as long as a large file over a slow line does; it
     * ends as [ImportDownload.Unreachable] once no byte has arrived for a minute. [onProgress] is told the bytes received
     * so far and the declared length, if any, as they arrive. What [openSink] or the sink throws (no room for the file)
     * is thrown on unchanged, never read as the network failing.
     */
    suspend fun download(
        url: String,
        maxSizeBytes: Long,
        openSink: suspend (announcedSize: Long?) -> suspend (bytes: ByteArray, offset: Int, length: Int) -> Unit,
        onProgress: (receivedBytes: Long, totalBytes: Long?) -> Unit,
    ): ImportDownload

    /**
     * The picture a song's `{image: src=…}` names by its address, downloaded as [download] would but read into memory
     * ([ImportDownload.File.bytes]), and asked only of a public `https` host on every hop (never the local network, since
     * the address comes from a file rather than from the user's own hand alone), giving up after a bounded time; any
     * other address is [ImportDownload.Missing] without a request. Whether the bytes are a picture is the caller's question, by content.
     */
    suspend fun downloadPicture(url: String, maxSizeBytes: Long): ImportDownload
}
