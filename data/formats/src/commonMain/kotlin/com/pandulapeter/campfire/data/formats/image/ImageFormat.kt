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
 * The picture formats a song may show: what the image decoder reads on every platform. A file's format is told by its
 * first bytes ([ImageInfo.of]), never by its name, and [extension] is the one Campfire writes it under.
 */
enum class ImageFormat(
    val extension: String,
    val mimeType: String,
) {
    PNG(extension = ".png", mimeType = "image/png"),
    JPEG(extension = ".jpg", mimeType = "image/jpeg"),

    /** Only its first frame is drawn. */
    GIF(extension = ".gif", mimeType = "image/gif"),
    WEBP(extension = ".webp", mimeType = "image/webp"),
}
