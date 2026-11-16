/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.platform

import com.pandulapeter.campfire.data.model.image.ImageSize
import kotlin.math.sqrt

/** The formats a picture is re-encoded into: ones every platform both writes and shows. */
internal enum class PictureEncoding(
    val mimeType: String,
) {
    JPEG(mimeType = "image/jpeg"),

    /** Keeps transparency, and keeps line art (a scanned chart) crisp, but is large for a photo. */
    PNG(mimeType = "image/png"),
}

/**
 * One try at encoding a decoded picture: as [encoding], at [quality] (JPEG's, from 0 to 1; a PNG is lossless), at [scale]
 * times each side of the size [reencodedSizeOf] fits the picture to. A JPEG of a picture with transparency is flattened
 * onto white.
 */
internal data class PictureAttempt(
    val encoding: PictureEncoding,
    val quality: Float,
    val scale: Float,
)

/** The longest side a re-encoded picture may have: sharp on any screen and printed page, and small to decode. */
internal const val MAX_REENCODED_SIDE = 4_096

/** The most pixels a re-encoded picture may have, about 48 MB decoded, and within iOS Safari's largest canvas. */
internal const val MAX_REENCODED_PIXELS = 12_000_000L

/**
 * The size a picture of [width] by [height] pixels (upright, as it is shown) is re-encoded at for [scale]: never larger
 * than it is, its long side within [MAX_REENCODED_SIDE] and its area within [MAX_REENCODED_PIXELS], and its proportions
 * kept; each side at least one pixel. Every platform's re-encoder decodes to this size, so it is the one rule of them all.
 */
internal fun reencodedSizeOf(width: Int, height: Int, scale: Float = 1f): ImageSize {
    val fit = minOf(
        1.0,
        MAX_REENCODED_SIDE.toDouble() / maxOf(width, height),
        sqrt(MAX_REENCODED_PIXELS.toDouble() / (width.toLong() * height)),
    ) * scale
    // A side the fit lands exactly on (8,000 halved to 4,096) must not lose a pixel to the floating point's rounding.
    var fittedWidth = (width * fit + ROUNDING_SLACK).toInt().coerceIn(1, width)
    var fittedHeight = (height * fit + ROUNDING_SLACK).toInt().coerceIn(1, height)
    while (fittedWidth.toLong() * fittedHeight > MAX_REENCODED_PIXELS) {
        if (fittedWidth >= fittedHeight) fittedWidth-- else fittedHeight--
    }
    return ImageSize(width = fittedWidth, height = fittedHeight)
}

private const val ROUNDING_SLACK = 1e-6
