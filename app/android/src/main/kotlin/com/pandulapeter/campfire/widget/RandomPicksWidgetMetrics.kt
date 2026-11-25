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

import com.pandulapeter.campfire.presentation.ui.widget.RANDOM_PICKS_MAX_ROWS

/** How many songs the random picks widget lists at a height, all in dp. */
internal object RandomPicksWidgetMetrics {

    const val HEADER_HEIGHT = 40
    const val ROW_HEIGHT = 44

    /** The height [rows] songs need, under the header and inside the padding. */
    fun heightFor(rows: Int) = 2 * WIDGET_PADDING + HEADER_HEIGHT + rows * ROW_HEIGHT

    /** How many songs fit [height], at least one, so that a widget squeezed below one row still shows a song. */
    fun rowsFitting(height: Float) = ((height - 2 * WIDGET_PADDING - HEADER_HEIGHT) / ROW_HEIGHT).toInt().coerceIn(1, RANDOM_PICKS_MAX_ROWS)
}
