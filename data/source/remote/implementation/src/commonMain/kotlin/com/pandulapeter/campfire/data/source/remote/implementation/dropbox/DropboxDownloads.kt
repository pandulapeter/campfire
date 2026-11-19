/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.implementation.dropbox

import com.pandulapeter.campfire.data.source.remote.api.sync.SyncDocumentTooLargeException
import com.pandulapeter.campfire.data.source.remote.api.sync.model.RemoteDocument
import com.pandulapeter.campfire.data.source.remote.implementation.crypto.dropboxContentHash
import com.pandulapeter.campfire.data.source.remote.implementation.network.BrowserCallTimeout
import com.pandulapeter.campfire.data.source.remote.implementation.network.HttpClientHolder
import com.pandulapeter.campfire.data.source.remote.implementation.network.toAsciiJsonString
import io.ktor.client.call.save
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.utils.io.exhausted
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.readBuffer
import kotlinx.io.readByteArray
import kotlinx.serialization.SerializationException

/**
 * Downloads of library files and of the folder's own documents, for [DropboxSyncProvider.download] and
 * [DropboxSyncProvider.downloadDocument].
 *
 * The body is streamed inside the one call [DropboxTransport.request] retries, so a transfer that breaks mid-body is
 * retried like any other, and one over its limit (the caller's for a library file, [maxDocumentSize] for a document)
 * is never held in memory: leaving `execute` drops the rest of it. Nothing is thrown inside that call for a body over the limit, because the retry loop would send the call again
 * and its last attempt would become the run's network failure; it is refused once the call returns, sent once.
 */
internal class DropboxDownloads(
    private val httpClientHolder: HttpClientHolder,
    private val requests: DropboxTransport,
    private val maxDocumentSize: Long,
) {

    /** A library file over the limit fails as that one file, like any other answer that is not the whole file. */
    suspend fun download(path: String, maxSize: Long): RemoteDocument {
        val download = streamed(path, maxSize)
        download.response.ensureSuccessful()
        val metadata = download.metadataOrThrow()
        val bytes = download.bytes
            ?: throw DropboxApiException(download.response.status.value, "the download is larger than $maxSize bytes")
        return downloadedDocument(download.response, metadata, bytes)
    }

    /** A document over the limit is refused with its revision, read from the header before the body. */
    suspend fun downloadDocument(path: String): RemoteDocument? {
        val download = streamed(path, maxDocumentSize)
        val response = download.response
        if (response.status == HttpStatusCode.Conflict && response.errorSummary().startsWith("path/not_found")) return null
        response.ensureSuccessful()
        val metadata = download.metadataOrThrow()
        val bytes = download.bytes ?: throw SyncDocumentTooLargeException(revision = metadata.rev)
        return downloadedDocument(response, metadata, bytes)
    }

    /**
     * The body of a successful download, checked against the size and content hash Dropbox sent with it. A body cut
     * short without a declared length - an HTTP/1.0 proxy, a connection closed at a chunk boundary - is otherwise
     * written as the song and indexed as in step, and the next edit uploads it over the full one. A mismatch fails only
     * this file: nothing is written, and the next run downloads it again, while a run ended by one file that keeps
     * failing would never finish the rest of the library. Both are compared against the decoded bytes, so a transfer
     * the engine decompressed is compared correctly.
     */
    private fun downloadedDocument(response: HttpResponse, metadata: DropboxFileMetadata, bytes: ByteArray): RemoteDocument {
        if (metadata.size != null && bytes.size.toLong() != metadata.size ||
            metadata.contentHash != null && dropboxContentHash(bytes) != metadata.contentHash
        ) {
            throw DropboxApiException(response.status.value, "the download arrived incomplete (${bytes.size} of ${metadata.size} bytes)")
        }
        return RemoteDocument(bytes = bytes, revision = metadata.rev)
    }

    private suspend fun streamed(path: String, maxSize: Long): Download {
        var metadata: DropboxFileMetadata? = null
        var bytes: ByteArray? = null
        val response = requests.request { accessToken ->
            metadata = null
            bytes = null
            httpClientHolder.client().preparePost(DropboxSyncProvider.DOWNLOAD_URL) {
                attributes.put(BrowserCallTimeout, DropboxTransport.downloadBrowserTimeoutMillis(maxSize))
                header("Authorization", "Bearer $accessToken")
                header("Dropbox-API-Arg", """{"path":${path.toAsciiJsonString()}}""")
            }.execute { response ->
                if (response.status.isSuccess()) {
                    metadata = downloadMetadataOrNull(response)
                    bytes = boundedBody(response, declaredSize = metadata?.size, maxSize = maxSize)
                    response
                } else {
                    // The retry loop, ensureSuccessful, and downloadDocument's not_found check each read an error's body,
                    // which a streamed answer allows once: this small answer is kept in memory like any other call's.
                    response.call.save().response
                }
            }
        }
        return Download(response, metadata, bytes)
    }

    /**
     * Null, without reading the body, where Dropbox declares it over [maxSize], and null where more than that arrives. A
     * declared size is read straight into an array of that size, which holds a 50 MB file once; only an undeclared one
     * goes through a growing buffer. What a body shorter or longer than declared brings is the caller's to check, as it
     * checks every download's hash.
     */
    private suspend fun boundedBody(response: HttpResponse, declaredSize: Long?, maxSize: Long): ByteArray? {
        if (declaredSize != null && declaredSize > maxSize) return null
        val channel = response.bodyAsChannel()
        if (declaredSize == null || declaredSize < 0) return channel.readBuffer(maxSize + 1L).readByteArray().takeIf { it.size <= maxSize }
        val bytes = ByteArray(declaredSize.toInt())
        var filled = 0
        while (filled < bytes.size) {
            val read = channel.readAvailable(bytes, filled, bytes.size - filled)
            if (read < 0) return bytes.copyOf(filled)
            filled += read
        }
        if (channel.exhausted()) return bytes
        val rest = channel.readBuffer(maxSize + 1L - declaredSize).readByteArray()
        return (bytes + rest).takeIf { it.size <= maxSize }
    }

    /**
     * A download answers with the file itself, so its metadata - the revision the next upload has to name, its size and
     * its hash - comes in a header instead, which Dropbox also exposes to a page's script.
     */
    private fun downloadMetadataOrNull(response: HttpResponse) = try {
        response.headers[DropboxSyncProvider.DOWNLOAD_RESULT_HEADER]?.let { json.decodeFromString<DropboxFileMetadata>(it) }
    } catch (exception: SerializationException) {
        null
    }?.takeIf { it.rev.isNotEmpty() }

    private fun Download.metadataOrThrow() = metadata ?: throw DropboxApiException(response.status.value, "the download named no revision")

    /** The answer to a download and, for a success, its metadata and its body, null where the body is over the limit. */
    private class Download(
        val response: HttpResponse,
        val metadata: DropboxFileMetadata?,
        val bytes: ByteArray?,
    )
}
