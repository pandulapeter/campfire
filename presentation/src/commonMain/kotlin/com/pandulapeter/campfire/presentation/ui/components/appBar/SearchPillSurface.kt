/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.appBar

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.Constraints
import com.pandulapeter.campfire.presentation.ui.components.tabChrome.SEARCH_PILL_CONTENT_Z_INDEX
import com.pandulapeter.campfire.presentation.ui.components.tabChrome.SEARCH_PILL_Z_INDEX
import com.pandulapeter.campfire.presentation.ui.components.tabChrome.SharedTabChrome
import com.pandulapeter.campfire.presentation.ui.components.tabChrome.sharedTabChrome
import com.pandulapeter.campfire.presentation.ui.components.tabChrome.tabChromeForeground
import kotlin.math.roundToInt

/**
 * The search pill as a node of its own, laid out at the pill's rectangle on this frame ([bounds], the bar's own, never
 * mirrored), for a tab swap to carry over: [SearchableTopAppBar] draws the pill behind itself, between its buttons and
 * its field, where no node stands, so this one is the rectangle [sharedTabChrome] shares with the other tab's pill, or
 * scales on its own where that tab has none.
 *
 * It draws the pill only while the tabs swap, when the bar leaves it out: drawn over both screens then, the bar's own
 * would show under it in the screen fading in, a second pill waiting where the travelling one is still on its way to.
 * Where it is placed on each frame of a swap is written to [pill] ([SearchPill.travellingBounds]) for what stands on the
 * pill to ride it, see [searchPillForeground]. Laid out from the bar's rectangles, which are only known once the
 * buttons and the field are placed, it is a frame behind them as the search opens and closes, which no swap lasts long
 * enough to show.
 */
@Composable
internal fun SearchPillSurface(
    modifier: Modifier = Modifier,
    sharedTabChrome: SharedTabChrome?,
    pill: SearchPill,
    bounds: () -> Rect,
) = Box(
    modifier = modifier
        .layout { measurable, constraints ->
            val pillBounds = bounds()
            val placeable = measurable.measure(
                Constraints.fixed(
                    width = pillBounds.width.roundToInt().coerceAtLeast(0),
                    height = pillBounds.height.roundToInt().coerceAtLeast(0),
                ),
            )
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeable.place(x = pillBounds.left.roundToInt(), y = pillBounds.top.roundToInt())
            }
        }
        .sharedTabChrome(chrome = sharedTabChrome, zIndex = SEARCH_PILL_Z_INDEX, isContainerDrawnOutsideSwaps = false)
        // After the shared bounds, which place what follows them at the travelling rectangle.
        .onPlaced { coordinates ->
            pill.travellingBounds = pill.bar?.takeIf { it.isAttached }?.localBoundingBoxOf(coordinates, clipBounds = false)
        },
)

/**
 * The bar's row drawn over the pill while two tabs swap ([tabChromeForeground]): the buttons or the field standing on
 * the pill ([bounds]) ride it to the other tab's and are clipped to it, the rest only fades.
 */
internal fun Modifier.searchPillForeground(
    chrome: SharedTabChrome?,
    pill: SearchPill,
    bounds: () -> Rect,
) = tabChromeForeground(
    chrome = chrome,
    zIndex = SEARCH_PILL_CONTENT_Z_INDEX,
    partBounds = bounds,
    travellingBounds = { pill.travellingBounds },
)
