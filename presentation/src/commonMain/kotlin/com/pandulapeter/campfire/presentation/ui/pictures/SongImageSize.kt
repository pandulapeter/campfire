/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.pictures

import androidx.compose.runtime.Immutable

/** What a picture a song names is: [Known] by its header, or [Missing] (no such file, or one no platform shows). */
@Immutable
internal sealed interface SongImageSize {

    /** Read from the picture's header, in pixels, its rotation applied. */
    data class Known(val width: Int, val height: Int) : SongImageSize

    data object Missing : SongImageSize
}
