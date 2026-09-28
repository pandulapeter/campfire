/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.api

import com.pandulapeter.campfire.data.model.domain.CoverArtCandidate
import com.pandulapeter.campfire.data.model.domain.CoverArtQuery

/**
 * The cover images the songs name, and the search that recommends one.
 *
 * An image is read from the device's own copy where there is one and downloaded otherwise, the copy being written as
 * it arrives, so that a cover seen once is there without a network. Copies no song names any more are deleted after
 * the library has been read, and a search result's thumbnail, which is fetched the same way, goes with them.
 */
interface CoverArtRepository {

    /**
     * The bytes of the image at [url], or null where there is none to show: nothing there, not an image, or no
     * network. Several callers asking for one address at once share one download, and an address that failed is not
     * asked again for a while — for the rest of the session where it answered with something that is not a cover,
     * and for a minute where it could not be reached — so that a dead address is not requested on every scroll.
     */
    suspend fun getCoverArt(url: String): ByteArray?

    /** See `CoverArtSearchRemoteSource.searchCoverArt`, whose failures are thrown as they are. */
    suspend fun searchCoverArt(query: CoverArtQuery, onBusy: () -> Unit): List<CoverArtCandidate>
}
