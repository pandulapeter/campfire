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

import com.pandulapeter.campfire.presentation.ui.widget.RANDOM_PICKS_COUNTS

/** How many songs the random picks widget lists at a height, all in dp. */
internal object RandomPicksWidgetMetrics {

    const val HEADER_HEIGHT = 40
    const val ROW_HEIGHT = 44

    /** The most songs a widget lists, as many as it can be set to draw. */
    val MAX_ROWS = RANDOM_PICKS_COUNTS.last

    /**
     * The compact layout's padding above and below its one song, less than the widget's own: a launcher's shortest cell
     * can be as short as 56 dp.
     */
    const val COMPACT_PADDING = 4

    /** The height of the compact layout: one song without the header, inside its padding. */
    const val COMPACT_HEIGHT = 2 * COMPACT_PADDING + ROW_HEIGHT

    /** The height [rows] songs need, under the header and inside the padding. */
    fun heightFor(rows: Int) = 2 * WIDGET_PADDING + HEADER_HEIGHT + rows * ROW_HEIGHT

    /** Whether [height] is too short for the header and a song, which leaves the compact layout. */
    fun isCompact(height: Float) = height < heightFor(1)

    /** How many songs fit [height] under the header, at least one. */
    fun rowsFitting(height: Float) = ((height - 2 * WIDGET_PADDING - HEADER_HEIGHT) / ROW_HEIGHT).toInt().coerceIn(1, MAX_ROWS)
}
