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

/** A GIF's logical screen size: the canvas its first frame, the one drawn, is shown on. */
internal object GifHeader {

    fun read(header: HeaderBytes): ImageInfo? {
        if (!header.startsWith("GIF87a") && !header.startsWith("GIF89a")) return null
        return ImageInfo.sized(ImageFormat.GIF, header.u16le(6).toLong(), header.u16le(8).toLong())
    }
}
