/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.image

/** What became of a picture or an attachment handed to the library or to the editor's draft. */
sealed interface ImageIntake {

    /** Kept under [name], which is the name a song's `{image}` directive or attachment line names it by. */
    data class Accepted(val name: String) : ImageIntake

    data class Refused(val reason: ImageRefusal) : ImageIntake
}

/**
 * Why a picture was not kept. The library never converts or shrinks one; a picture the user adds themselves that is
 * refused as too large or as a HEIC or AVIF photo is re-encoded by `:presentation` first, and this is its own reason where
 * that fails.
 */
enum class ImageRefusal {

    /** Larger than `ImportLimits.MAX_IMAGE_FILE_SIZE`, or for an attachment `ImportLimits.MAX_ATTACHMENT_FILE_SIZE`. */
    TOO_LARGE,

    /** More pixels than every platform can decode safely (`ImageInfo.MAX_PIXELS`, 25 megapixels), whatever its file size. */
    TOO_MANY_PIXELS,

    /** A picture of a format some platform cannot show (HEIC, AVIF, SVG), or an attachment of a type not taken (`LibraryFiles.ATTACHMENT_EXTENSIONS`). */
    UNSUPPORTED_FORMAT,

    /** Not a picture at all, or one whose header cannot be read. */
    NOT_AN_IMAGE,
}
