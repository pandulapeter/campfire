/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.images

import com.pandulapeter.campfire.data.model.importing.ImportLimits

interface DownloadPictureUseCase {

    /**
     * Downloads the picture at [url] for the user to add (the editor's From a link, a missing picture's Download), read as
     * far as a picture picked from the files is ([ImportLimits.MAX_PICKED_IMAGE_FILE_SIZE]), since it is made to fit
     * afterwards as one is. Whether it is a picture is told by its bytes, never by what the host calls it. Nothing is
     * kept; only a cancellation is thrown.
     */
    suspend operator fun invoke(url: String): PictureDownload
}

/** What downloading a picture from an address came to, see [DownloadPictureUseCase]. */
sealed interface PictureDownload {

    /** A picture by its bytes, which may still be too large or of a format the library does not keep as it is. */
    class Picture(val bytes: ByteArray) : PictureDownload

    /** Something other than a picture: a web page about the picture, most often, rather than the picture itself. */
    data object NotAPicture : PictureDownload

    /** Larger than [ImportLimits.MAX_PICKED_IMAGE_FILE_SIZE]; nothing of it is kept. */
    data object TooLarge : PictureDownload

    /** Nothing at the address, or an address the app does not ask (not public `https`); asking again gets the same. */
    data object Missing : PictureDownload

    /**
     * No answer: the host could not be reached, did not answer in time, or, in a browser, did not allow the page to
     * download it, which looks the same to the app.
     */
    data object Unreachable : PictureDownload
}
