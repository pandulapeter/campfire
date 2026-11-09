/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import androidx.compose.ui.unit.dp

internal val PAGE_TOP_PADDING = 8.dp // Inside the scroll, above the first row, and read with it.

internal val PAGE_HORIZONTAL_PADDING = 8.dp
internal val STEP_BUTTON_SIZE = 40.dp
internal val STEP_BUTTON_GAP = 8.dp

/** How far the step buttons are from the bottom of the pages, above the setlist's pager bar where there is one. */
internal val STEP_BUTTON_EDGE_MARGIN = 8.dp

/**
 * How much room the row of step buttons takes at the bottom of a song turned by column, under the bottom padding the
 * pages have anyway, so that it never covers a line.
 */
internal val STEP_ROW_HEIGHT = STEP_BUTTON_EDGE_MARGIN + STEP_BUTTON_SIZE
