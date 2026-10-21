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

import com.pandulapeter.campfire.data.model.importing.ImportLimits
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
import io.ktor.utils.io.readBuffer
import kotlinx.io.readByteArray
import kotlinx.serialization.SerializationException

/**
 * Downloads of library files and of the folder's own documents, for [DropboxSyncProvider.download] and
 * [DropboxSyncProvider.downloadDocument].
 *
 * The body is streamed inside the one call [DropboxTransport.request] retries, so a transfer that breaks mid-body is
 * retried like any other, and one over [MAXIMUM_DOWNLOAD_SIZE] is never held in memory: leaving `execute` drops the rest
 * of it. Nothing is thrown inside that call for a body over the limit, because the retry loop would send the call again
 * and its last attempt would become the run's network failure; it is refused once the call returns, sent once.
 */
internal class DropboxDownloads(
    private val httpClientHolder: HttpClientHolder,
    private val requests: DropboxTransport,
) {

    /** A library file over the limit fails as that one file, like any other answer that is not the whole file. */
    suspend fun download(path: String): RemoteDocument {
        val download = streamed(path)
        download.response.ensureSuccessful()
        val metadata = download.metadataOrThrow()
        val bytes = download.bytes
            ?: throw DropboxApiException(download.response.status.value, "the download is larger than $MAXIMUM_DOWNLOAD_SIZE bytes")
        return downloadedDocument(download.response, metadata, bytes)
    }

    /** A document over the limit is refused with its revision, read from the header before the body. */
    suspend fun downloadDocument(path: String): RemoteDocument? {
        val download = streamed(path)
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

    private suspend fun streamed(path: String): Download {
        var metadata: DropboxFileMetadata? = null
        var bytes: ByteArray? = null
        val response = requests.request { accessToken ->
            metadata = null
            bytes = null
            httpClientHolder.client().preparePost(DropboxSyncProvider.DOWNLOAD_URL) {
                attributes.put(BrowserCallTimeout, DropboxTransport.DOWNLOAD_BROWSER_TIMEOUT_MILLIS)
                header("Authorization", "Bearer $accessToken")
                header("Dropbox-API-Arg", """{"path":${path.toAsciiJsonString()}}""")
            }.execute { response ->
                if (response.status.isSuccess()) {
                    metadata = downloadMetadataOrNull(response)
                    bytes = boundedBody(response, declaredSize = metadata?.size)
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

    /** Null, without reading the body, where Dropbox declares it over the limit, and null where more than that arrives. */
    private suspend fun boundedBody(response: HttpResponse, declaredSize: Long?): ByteArray? {
        if (declaredSize != null && declaredSize > MAXIMUM_DOWNLOAD_SIZE) return null
        val bytes = response.bodyAsChannel().readBuffer(MAXIMUM_DOWNLOAD_SIZE + 1L).readByteArray()
        return bytes.takeIf { it.size <= MAXIMUM_DOWNLOAD_SIZE }
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

    companion object {

        /**
         * The library's own limit for a text file. A `preferences.json` of thousands of songs with chords and tags, and a
         * shared links list of thousands of shares, stay far under it, so it never refuses a document of a real library.
         */
        const val MAXIMUM_DOWNLOAD_SIZE = ImportLimits.MAX_TEXT_FILE_SIZE
    }
}
