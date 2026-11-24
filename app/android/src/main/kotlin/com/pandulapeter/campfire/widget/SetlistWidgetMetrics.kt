/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.widget

/** How many of a setlist's song titles the setlist widget lists at a height, all in dp. */
internal object SetlistWidgetMetrics {

    /** The label, the title, the date, and the song count. */
    const val HEADER_HEIGHT = 84
    const val SONG_LINE_HEIGHT = 18

    /** The numbers of song titles a layout is made for, the widget's smallest size showing none. */
    val SONG_LINE_STEPS = listOf(0, 2, 5, 8, 12)

    /** The height [songLines] titles need, under the header and inside the padding. */
    fun heightFor(songLines: Int) = 2 * WIDGET_PADDING + HEADER_HEIGHT + songLines * SONG_LINE_HEIGHT

    /** How many titles fit [height]. */
    fun songLinesFitting(height: Float) = ((height - 2 * WIDGET_PADDING - HEADER_HEIGHT) / SONG_LINE_HEIGHT).toInt().coerceAtLeast(0)
}
