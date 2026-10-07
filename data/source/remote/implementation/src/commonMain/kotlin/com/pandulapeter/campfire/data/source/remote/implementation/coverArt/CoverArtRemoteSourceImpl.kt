/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.implementation.coverArt

import com.pandulapeter.campfire.data.source.remote.api.CoverArtRemoteSource
import com.pandulapeter.campfire.data.source.remote.api.model.CoverArtDownload
import com.pandulapeter.campfire.data.source.remote.implementation.network.HttpClientHolder
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.parseUrl
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.io.readByteArray
import org.koin.core.annotation.Single

/**
 * Downloads cover images from wherever a song says they are. Any address a song names is asked, so what comes back is
 * trusted no further than its headers and its size: an answer that is not an image, or that is larger than
 * [MAXIMUM_SIZE_BYTES], is [CoverArtDownload.Missing] and never read to its end.
 */
@Single
internal class CoverArtRemoteSourceImpl(
    private val httpClientHolder: HttpClientHolder,
) : CoverArtRemoteSource {

    override suspend fun downloadCoverArt(url: String): CoverArtDownload {
        if (parseUrl(url) == null) return CoverArtDownload.Missing
        return try {
            httpClientHolder.client().prepareGet(url).execute { response ->
                when {
                    response.status.isTransient -> CoverArtDownload.Unreachable
                    !response.status.isSuccess() -> CoverArtDownload.Missing
                    response.contentType()?.match(ContentType.Image.Any) != true -> CoverArtDownload.Missing
                    (response.contentLength() ?: 0) > MAXIMUM_SIZE_BYTES -> CoverArtDownload.Missing
                    else -> {
                        // A length is not promised, so the body is read one byte past the limit to find out.
                        val bytes = response.bodyAsChannel().readRemaining(MAXIMUM_SIZE_BYTES + 1L).readByteArray()
                        if (bytes.isEmpty() || bytes.size > MAXIMUM_SIZE_BYTES) CoverArtDownload.Missing else CoverArtDownload.Image(bytes)
                    }
                }
            }
        } catch (exception: CancellationException) {
            // Only a cancellation of this coroutine is one; anything else that arrives as one is the transport failing.
            currentCoroutineContext().ensureActive()
            CoverArtDownload.Unreachable
        } catch (throwable: Throwable) {
            // The browser engine reports a failed fetch, a refused CORS check included, as a kotlin.Error.
            CoverArtDownload.Unreachable
        }
    }

    /** The answers that say "not now" rather than "not here", which a later attempt may well get past. */
    private val HttpStatusCode.isTransient
        get() = this == HttpStatusCode.RequestTimeout || this == HttpStatusCode.TooManyRequests || value >= 500

    private companion object {
        const val MAXIMUM_SIZE_BYTES = 5 * 1024 * 1024
    }
}
