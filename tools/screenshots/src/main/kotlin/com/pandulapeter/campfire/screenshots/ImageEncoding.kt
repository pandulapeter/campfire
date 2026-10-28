/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.screenshots

import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorInfo
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Surface

/**
 * [image] encoded as a file with [extension] says: a PNG lossless, for the stores, and a WebP small enough for the
 * README. A PNG is written without an alpha channel unless [hasAlpha], since App Store Connect refuses a screenshot that
 * has one and Play a feature graphic, however opaque its pixels: Skia leaves the channel out of an opaque image.
 */
internal fun encodeImage(image: Image, extension: String, hasAlpha: Boolean = false) = checkNotNull(
    when (extension) {
        "png" -> (if (hasAlpha) image else image.opaque()).encodeToData(EncodedImageFormat.PNG)
        "webp" -> image.encodeToData(EncodedImageFormat.WEBP, WEBP_QUALITY)
        else -> error("No encoding for .$extension")
    },
).bytes

private fun Image.opaque(): Image {
    val surface = Surface.makeRaster(ImageInfo(ColorInfo(ColorType.N32, ColorAlphaType.OPAQUE, null), width, height))
    surface.canvas.drawImage(this, 0f, 0f)
    return surface.makeImageSnapshot()
}

/** Small enough for the README's banners to weigh a few dozen kilobytes each, with their screens' text still sharp. */
private const val WEBP_QUALITY = 82
