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
import com.pandulapeter.campfire.data.source.remote.api.ImportDownloadRemoteSource
import com.pandulapeter.campfire.data.source.remote.implementation.network.HttpClientHolder
import com.pandulapeter.campfire.data.source.remote.implementation.network.PublicHostsOnly
import com.pandulapeter.campfire.data.source.remote.implementation.network.RefusedAddressException
import com.pandulapeter.campfire.data.source.remote.implementation.network.isCutShort
import com.pandulapeter.campfire.data.source.remote.implementation.network.isPublicHttpsAddress
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
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
 * what makes a share link with no extension work. An answer larger than the ceiling is never read to its end. A picture a
 * song names by its address comes the same way, from public `https` hosts only and within [PICTURE_TIMEOUT_MILLIS].
 */
@Single
internal class ImportDownloadRemoteSourceImpl(
    private val httpClientHolder: HttpClientHolder,
) : ImportDownloadRemoteSource {

    override suspend fun download(url: String, maxSizeBytes: Long): ImportDownload {
        val address = parseUrl(url) ?: return ImportDownload.Missing
        return fetch(address = address, maxSizeBytes = maxSizeBytes) {}
    }

    override suspend fun downloadPicture(url: String, maxSizeBytes: Long): ImportDownload {
        val address = parseUrl(url)?.takeIf(::isPublicHttpsAddress) ?: return ImportDownload.Missing
        return fetch(address = address, maxSizeBytes = maxSizeBytes) {
            attributes.put(PublicHostsOnly, Unit)
            timeout { requestTimeoutMillis = PICTURE_TIMEOUT_MILLIS }
        }
    }

    private suspend fun fetch(address: Url, maxSizeBytes: Long, configure: HttpRequestBuilder.() -> Unit): ImportDownload = try {
        httpClientHolder.client().prepareGet(address, configure).execute { response ->
            when {
                response.status.isTransient -> ImportDownload.Unreachable
                !response.status.isSuccess() -> ImportDownload.Missing
                (response.contentLength() ?: 0) > maxSizeBytes -> ImportDownload.TooLarge
                else -> {
                    // A length is not promised, so the body is read one byte past the limit to find out, and a declared one
                    // the body falls short of is a transfer cut off, not a smaller file.
                    val bytes = response.bodyAsChannel().readBuffer(maxSizeBytes + 1).readByteArray()
                    when {
                        bytes.isEmpty() -> ImportDownload.Missing
                        bytes.size > maxSizeBytes -> ImportDownload.TooLarge
                        response.isCutShort(bytes.size) -> ImportDownload.Unreachable
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
    } catch (exception: RefusedAddressException) {
        // A redirect into the local network is the address's answer, not the network failing.
        ImportDownload.Missing
    } catch (exception: CancellationException) {
        // Only a cancellation of this coroutine is one; anything else that arrives as one is the transport failing.
        currentCoroutineContext().ensureActive()
        ImportDownload.Unreachable
    } catch (throwable: Throwable) {
        // The browser engine reports a failed fetch, a refused CORS check included, as a kotlin.Error.
        ImportDownload.Unreachable
    }

    /** The answers that say "not now" rather than "not here", which a later attempt may well get past. */
    private val HttpStatusCode.isTransient
        get() = this == HttpStatusCode.RequestTimeout || this == HttpStatusCode.TooManyRequests || value >= 500

    internal companion object {

        /**
         * How long one picture's download may take, body included: the largest picture read (40 MB) at a fair mobile
         * rate. What it is for is a host that accepts the connection and sends nothing, which must not hold an import's
         * review back, or the editor's progress bar up, for the ten minutes the browser otherwise allows.
         */
        const val PICTURE_TIMEOUT_MILLIS = 120_000L
    }
}
