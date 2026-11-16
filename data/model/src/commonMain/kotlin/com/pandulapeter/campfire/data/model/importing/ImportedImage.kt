/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.importing

/**
 * A picture an incoming song's image directive names, as the archive it arrived in held it: [bytes] where the archive
 * had it and it was read, else why not ([problem]). [source] is the file the directive names, exactly as written, which
 * is what the import rewrites once it knows the name the library keeps the picture under.
 */
class ImportedImage(
    val source: String,
    val bytes: ByteArray?,
    val problem: Problem?,
) {

    override fun equals(other: Any?) = this === other || (
        other is ImportedImage && source == other.source && problem == other.problem &&
            (bytes === other.bytes || (bytes != null && other.bytes != null && bytes.contentEquals(other.bytes)))
        )

    override fun hashCode() = 31 * (31 * source.hashCode() + problem.hashCode()) + (bytes?.contentHashCode() ?: 0)

    override fun toString() = "ImportedImage(source=$source, size=${bytes?.size}, problem=$problem)"

    /** Why a picture a song names does not come into the library with it. The song comes all the same. */
    enum class Problem {

        /** Nothing in the archive answers to the name, or more than one picture does by its name alone. */
        MISSING,

        /** Larger than `ImportLimits.MAX_IMAGE_FILE_SIZE`, or past what is left of `ImportLimits.MAX_IMPORT_IMAGES_SIZE`. */
        TOO_LARGE,

        /** Damaged, encrypted, or compressed in a way the archive reader does not know. */
        UNREADABLE,

        /** A picture of a format some platform cannot show (HEIC, AVIF, SVG). */
        UNSUPPORTED_FORMAT,

        /** Not a picture at all. */
        NOT_AN_IMAGE,
    }
}
