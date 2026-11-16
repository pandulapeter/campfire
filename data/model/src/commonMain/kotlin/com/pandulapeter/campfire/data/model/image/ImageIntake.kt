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

/** What became of a picture handed to the library or to the editor's draft. */
sealed interface ImageIntake {

    /** Kept under [name], which is the name a song's `{image}` directive names it by. */
    data class Accepted(val name: String) : ImageIntake

    data class Refused(val reason: ImageRefusal) : ImageIntake
}

/** Why a picture was not kept. Nothing is converted or shrunk, since that would take an encoder on every platform. */
enum class ImageRefusal {

    /** Larger than `ImportLimits.MAX_IMAGE_FILE_SIZE`, the most sync moves. */
    TOO_LARGE,

    /** A picture of a format some platform cannot show (HEIC, AVIF, SVG). */
    UNSUPPORTED_FORMAT,

    /** Not a picture at all, or one whose header cannot be read. */
    NOT_AN_IMAGE,
}
