/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.api.model

/** What downloading a cover image came to, see [com.pandulapeter.campfire.data.source.remote.api.CoverArtRemoteSource]. */
sealed interface CoverArtDownload {

    class Image(val bytes: ByteArray) : CoverArtDownload

    /**
     * The address answered, and not with an image worth keeping: nothing there, not an image, or too large. Asking
     * again would get the same answer, so the caller need not ask again for as long as the app runs.
     */
    data object Missing : CoverArtDownload

    /** The host could not be reached or did not answer in time, which may be different a minute later. */
    data object Unreachable : CoverArtDownload
}
