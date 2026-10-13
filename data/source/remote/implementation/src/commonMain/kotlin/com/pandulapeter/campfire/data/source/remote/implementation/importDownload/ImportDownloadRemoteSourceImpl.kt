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

import com.pandulapeter.campfire.data.model.domain.ImportDownload
import com.pandulapeter.campfire.data.source.remote.api.ImportDownloadRemoteSource
import com.pandulapeter.campfire.data.source.remote.implementation.network.HttpClientHolder
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.http.parseUrl
import io.ktor.utils.io.readBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.io.readByteArray
import org.koin.core.annotation.Single

/**
 * Downloads a file to import from an address the user opened, once. Whatever the host says the file is, it is read as
 * bytes and handed to the import, which triages it by its name or, for a name it does not know, by its content; that is
 * what makes a share link with no extension work. An answer larger than the ceiling is never read to its end.
 */
@Single
internal class ImportDownloadRemoteSourceImpl(
    private val httpClientHolder: HttpClientHolder,
) : ImportDownloadRemoteSource {

    override suspend fun download(url: String, maxSizeBytes: Long): ImportDownload {
        if (parseUrl(url) == null) return ImportDownload.Missing
        return try {
            httpClientHolder.client().prepareGet(url).execute { response ->
                when {
                    response.status.isTransient -> ImportDownload.Unreachable
                    !response.status.isSuccess() -> ImportDownload.Missing
                    (response.contentLength() ?: 0) > maxSizeBytes -> ImportDownload.TooLarge
                    else -> {
                        // A length is not promised, so the body is read one byte past the limit to find out.
                        val bytes = response.bodyAsChannel().readBuffer(maxSizeBytes + 1).readByteArray()
                        when {
                            bytes.isEmpty() -> ImportDownload.Missing
                            bytes.size > maxSizeBytes -> ImportDownload.TooLarge
                            // The address the redirects ended at names the file better than the one that was asked.
                            else -> ImportDownload.File(
                                name = importDownloadFileName(
                                    contentDisposition = response.headers[HttpHeaders.ContentDisposition],
                                    url = response.call.request.url.toString(),
                                ),
                                bytes = bytes,
                            )
                        }
                    }
                }
            }
        } catch (exception: CancellationException) {
            // Only a cancellation of this coroutine is one; anything else that arrives as one is the transport failing.
            currentCoroutineContext().ensureActive()
            ImportDownload.Unreachable
        } catch (throwable: Throwable) {
            // The browser engine reports a failed fetch, a refused CORS check included, as a kotlin.Error.
            ImportDownload.Unreachable
        }
    }

    /** The answers that say "not now" rather than "not here", which a later attempt may well get past. */
    private val HttpStatusCode.isTransient
        get() = this == HttpStatusCode.RequestTimeout || this == HttpStatusCode.TooManyRequests || value >= 500
}
