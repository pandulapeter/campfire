/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.list

import androidx.compose.material3.ListItem
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp

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
 * How far a card's trailing controls move toward its edge from the inset `ListItem` gives them, in a list whose fast
 * scroller takes a column [scrollerColumnWidth] wide ([FastScrollerColumn]). Without the scroller's column the cards
 * in the last column end where the app bar's New button does, and the overflow button stands under the button's
 * center. While the column opens or closes the controls follow it.
 */
internal fun listItemTrailingKeylineAdjustment(scrollerColumnWidth: Dp) = lerp(
    start = SCROLLER_LESS_TRAILING_KEYLINE_ADJUSTMENT,
    stop = SCROLLER_TRAILING_KEYLINE_ADJUSTMENT,
    fraction = (scrollerColumnWidth / FAST_SCROLLER_WIDTH).coerceIn(0f, 1f),
)

/**
 * The [listItemTrailingKeylineAdjustment] of the list the cards are in, provided by the list along with its scroller.
 * Outside a list the cards keep the room a scroller would take.
 *
 * A function, read only while the cards and headers are laid out or placed: the adjustment follows the scroller's
 * column on every frame of its spring, and a value read while composing would compose every card again on each. The
 * list provides one instance for the life of its column, so the local itself never changes.
 */
internal val LocalListItemTrailingKeylineAdjustment = compositionLocalOf { ScrollerColumnTrailingKeylineAdjustment }

private val ScrollerColumnTrailingKeylineAdjustment: () -> Dp = { listItemTrailingKeylineAdjustment(FAST_SCROLLER_WIDTH) }

private val SCROLLER_TRAILING_KEYLINE_ADJUSTMENT = 12.dp
private val SCROLLER_LESS_TRAILING_KEYLINE_ADJUSTMENT = 16.dp
