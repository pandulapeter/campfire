/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.material3.ListItem
import androidx.compose.ui.unit.dp

/**
 * The x position the text of a [ListItem] starts at, before the card's own outer inset.
 */
internal val LIST_ITEM_KEYLINE = 16.dp

/**
 * How far a heading that takes a tap without drawing a ripple - a list's section header, the song details screen's
 * title - is dimmed while it is held, the way a text button on iOS answers a press.
 */
internal const val PRESSED_HEADING_ALPHA = 0.5f

/**
 * How far a card's trailing controls move toward its edge from the inset `ListItem` gives them: far enough that the
 * icon of the last one stands as far in from the card's end edge as the text does from its start.
 */
internal val LIST_ITEM_TRAILING_KEYLINE_ADJUSTMENT = 12.dp
