/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.implementation.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.parseUrl
import io.ktor.utils.io.readBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.io.readByteArray

/**
 * What a download fetched whole into memory comes to. [Missing] will not change (nothing there, a refusal, a hop into the
 * local network); [Unreachable] may soon (a transient status, a transport failure, refused CORS, a body cut short).
 */
internal sealed interface BoundedDownload {

    /** [contentDisposition] and [finalUrl] (the address the redirects ended at) are read before the response closes. */
    class Body(val bytes: ByteArray, val contentDisposition: String?, val finalUrl: String) : BoundedDownload

    data object Missing : BoundedDownload

    data object TooLarge : BoundedDownload

    data object Unreachable : BoundedDownload
}

/**
 * Downloads [url] whole, as long as it and every hop after it is a public `https` host ([isPublicHttpsAddress]); any
 * other address is [BoundedDownload.Missing] without a request. Nothing is read past [maxSizeBytes], and the whole call,
 * body included, is bounded by [timeoutMillis] so a host that accepts the connection and sends nothing cannot hold the
 * caller. [imagesOnly] refuses an answer whose content type is not an image as [BoundedDownload.Missing].
 */
internal suspend fun HttpClient.boundedPublicGet(url: String, maxSizeBytes: Long, timeoutMillis: Long, imagesOnly: Boolean): BoundedDownload {
    val address = parseUrl(url)?.takeIf(::isPublicHttpsAddress) ?: return BoundedDownload.Missing
    return try {
        prepareGet(address) {
            attributes.put(PublicHostsOnly, Unit)
            timeout { requestTimeoutMillis = timeoutMillis }
        }.execute { response ->
            response.refusal(maxSizeBytes) ?: when {
                imagesOnly && response.contentType()?.match(ContentType.Image.Any) != true -> BoundedDownload.Missing
                else -> {
                    // A length is not promised, so the body is read one byte past the limit to find out, and a declared one
                    // the body falls short of is a transfer cut off, not a smaller file.
                    val bytes = response.bodyAsChannel().readBuffer(maxSizeBytes + 1).readByteArray()
                    when {
                        bytes.isEmpty() -> BoundedDownload.Missing
                        bytes.size > maxSizeBytes -> BoundedDownload.TooLarge
                        response.isCutShort(bytes.size.toLong()) -> BoundedDownload.Unreachable
                        else -> BoundedDownload.Body(
                            bytes = bytes,
                            contentDisposition = response.headers[HttpHeaders.ContentDisposition],
                            finalUrl = response.call.request.url.toString(),
                        )
                    }
                }
            }
        }
    } catch (throwable: Throwable) {
        boundedFailureOf(throwable)
    }
}

/** Why [this] is not the file, decided before its body, or null where its body is to be read. */
internal fun HttpResponse.refusal(maxSizeBytes: Long): BoundedDownload? = when {
    status.isTransient -> BoundedDownload.Unreachable
    !status.isSuccess() -> BoundedDownload.Missing
    (contentLength() ?: 0) > maxSizeBytes -> BoundedDownload.TooLarge
    else -> null
}

/** What a download that threw [throwable] comes to; only a cancellation of this coroutine is thrown on. */
internal suspend fun boundedFailureOf(throwable: Throwable): BoundedDownload = when (throwable) {
    // A redirect into the local network is the address's answer, not the network failing.
    is RefusedAddressException -> BoundedDownload.Missing
    is CancellationException -> {
        // Only a cancellation of this coroutine is one; anything else that arrives as one is the transport failing.
        currentCoroutineContext().ensureActive()
        BoundedDownload.Unreachable
    }
    // The browser engine reports a failed fetch, a refused CORS check included, as a kotlin.Error, and a stalled
    // streamed download ends in a StalledException.
    else -> BoundedDownload.Unreachable
}

/** The answers that say "not now" rather than "not here", which a later attempt may well get past. */
private val HttpStatusCode.isTransient
    get() = this == HttpStatusCode.RequestTimeout || this == HttpStatusCode.TooManyRequests || value >= 500
