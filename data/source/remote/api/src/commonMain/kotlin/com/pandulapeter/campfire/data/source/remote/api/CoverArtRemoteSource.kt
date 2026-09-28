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

import com.pandulapeter.campfire.data.source.remote.api.model.CoverArtDownload

/**
 * Downloads the image a song's `{meta: cover …}` directive names. The address is the user's, so it may point at any
 * host: an implementation follows redirects, refuses anything that is not an image or that is larger than a cover
 * has any reason to be, and never throws for any of that, answering with what the caller should remember instead.
 */
interface CoverArtRemoteSource {

    /** The image at [url], or why there is none; only a cancellation is thrown. */
    suspend fun downloadCoverArt(url: String): CoverArtDownload
}
