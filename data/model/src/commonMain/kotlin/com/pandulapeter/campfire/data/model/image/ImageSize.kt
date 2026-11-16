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

/**
 * The size a picture of the library is shown at before scaling, in pixels, read from its header alone (its EXIF rotation
 * applied), so that a song lays its page out right from the first frame, before the picture is decoded.
 */
data class ImageSize(
    val width: Int,
    val height: Int,
)
