/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.formats.image

/**
 * A picture's [format] and the size it is shown at, read from its header alone so that a song's layout can reserve the
 * picture's room before it is decoded. [width] and [height] are as displayed: a JPEG its EXIF orientation turns by a
 * quarter has them swapped. Both are between 1 and [MAX_SIDE].
 */
data class ImageInfo(
    val format: ImageFormat,
    val width: Int,
    val height: Int,
) {

    companion object {

        /** No more of a file than this is ever looked at; a JPEG whose frame header lies past it is not read. */
        const val MAX_HEADER_BYTES = 64 * 1024

        /**
         * The longest side a header may claim. The layout trusts these numbers before anything is decoded, so a lying
         * header must not be able to ask for an enormous box; a panorama or a long scanned chart stays well within it.
         */
        const val MAX_SIDE = 32_768

        /**
         * The most pixels a picture may have: every platform but Android decodes the whole picture before scaling it to
         * the size drawn (Skia's lazy image), at four bytes a pixel, so a picture past this is never drawn anywhere. A
         * small file can claim a huge canvas (a flat PNG of 16,384 squared is a megabyte and a gigabyte decoded), which
         * would end the app on iOS and the web on every opening of its song. 25 megapixels keeps any camera's ordinary
         * photo (12 to 24) and costs at most about 100 MB for the moment it is decoded.
         */
        const val MAX_PIXELS = 25_000_000L

        /**
         * The format and displayed size of the picture [bytes] holds (the whole file or at least its first
         * [MAX_HEADER_BYTES]), or null for anything that is not a PNG, JPEG, GIF, or WebP with a readable header,
         * sides within [MAX_SIDE], and no more than [MAX_PIXELS]. Never throws.
         */
        fun of(bytes: ByteArray): ImageInfo? = read(bytes)?.takeIf { it.width.toLong() * it.height <= MAX_PIXELS }

        /** Whether [bytes] is a picture [of] would read but for having more than [MAX_PIXELS]. */
        fun isOverPixelLimit(bytes: ByteArray) = read(bytes)?.let { it.width.toLong() * it.height > MAX_PIXELS } == true

        private fun read(bytes: ByteArray): ImageInfo? {
            val header = HeaderBytes(bytes)
            return PngHeader.read(header) ?: GifHeader.read(header) ?: WebpHeader.read(header) ?: JpegHeader.read(header)
        }

        /** The info for a header's claimed size, or null when either side is outside 1..[MAX_SIDE]. */
        internal fun sized(format: ImageFormat, width: Long, height: Long) =
            if (width in 1..MAX_SIDE && height in 1..MAX_SIDE) ImageInfo(format, width.toInt(), height.toInt()) else null
    }
}
