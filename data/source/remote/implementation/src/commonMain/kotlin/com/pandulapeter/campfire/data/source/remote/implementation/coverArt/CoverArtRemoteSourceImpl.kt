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
import com.pandulapeter.campfire.data.source.remote.api.CoverArtDownload
import com.pandulapeter.campfire.data.source.remote.implementation.network.BoundedDownload
import com.pandulapeter.campfire.data.source.remote.implementation.network.HttpClientHolder
import com.pandulapeter.campfire.data.source.remote.implementation.network.boundedPublicGet
import com.pandulapeter.campfire.data.source.remote.implementation.network.isPublicHttpsAddress
import org.koin.core.annotation.Single

/**
 * Downloads cover images from wherever a song says they are, as long as that is a public `https` host on every hop
 * ([isPublicHttpsAddress]); any other address is [CoverArtDownload.Missing] without a request. What comes back is
 * trusted no further than its headers and its size: an answer that is not an image, or that is larger than
 * [MAXIMUM_SIZE_BYTES], is [CoverArtDownload.Missing] and never read to its end.
 */
@Single
internal class CoverArtRemoteSourceImpl(
    private val httpClientHolder: HttpClientHolder,
) : CoverArtRemoteSource {

    override suspend fun downloadCoverArt(url: String) = when (
        val download = httpClientHolder.client().boundedPublicGet(
            url = url,
            maxSizeBytes = MAXIMUM_SIZE_BYTES.toLong(),
            timeoutMillis = DOWNLOAD_TIMEOUT_MILLIS,
            imagesOnly = true,
        )
    ) {
        is BoundedDownload.Body -> CoverArtDownload.Image(download.bytes)
        // A cover past its ceiling is no cover, for good: a larger one is not on its way.
        BoundedDownload.Missing, BoundedDownload.TooLarge -> CoverArtDownload.Missing
        BoundedDownload.Unreachable -> CoverArtDownload.Unreachable
    }

    internal companion object {
        const val MAXIMUM_SIZE_BYTES = 5 * 1024 * 1024

        /**
         * How long one download may take, body included. A thumbnail makes it at any rate a phone gets, and the largest
         * cover allowed at ~85 KB/s; what it is for is a host that accepts the connection and sends nothing, which must
         * not hold one of the repository's four download slots for the ten minutes the browser otherwise allows.
         */
        const val DOWNLOAD_TIMEOUT_MILLIS = 60_000L
    }
}
