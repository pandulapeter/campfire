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
import com.pandulapeter.campfire.presentation.ui.components.tabChrome.SEARCH_PILL_CONTENT_Z_INDEX
import com.pandulapeter.campfire.presentation.ui.components.tabChrome.SEARCH_PILL_Z_INDEX
import com.pandulapeter.campfire.presentation.ui.components.tabChrome.SharedTabChrome
import com.pandulapeter.campfire.presentation.ui.components.tabChrome.sharedTabChromeSurface
import com.pandulapeter.campfire.presentation.ui.components.tabChrome.tabChromeForeground

/**
 * The search pill as a node of its own, laid over the whole bar, for a tab swap to carry over ([sharedTabChromeSurface]):
 * [SearchableTopAppBar] draws the pill behind itself, between its buttons and its field, where no node stands. It draws
 * the pill only while the tabs swap, when the bar leaves it out: drawn over both screens then, the bar's own would show
 * under it in the screen fading in, a second pill waiting where the travelling one is still on its way to. Its
 * rectangle is [bounds], the bar's own, never mirrored, read while drawing.
 */
@Composable
internal fun SearchPillSurface(
    modifier: Modifier = Modifier,
    sharedTabChrome: SharedTabChrome?,
    bounds: () -> Rect,
) = Box(modifier = modifier.sharedTabChromeSurface(chrome = sharedTabChrome, zIndex = SEARCH_PILL_Z_INDEX, bounds = bounds))

/**
 * The bar's row drawn over the pill while two tabs swap ([tabChromeForeground]): the buttons or the field standing on
 * the pill ([bounds]) ride it to the other tab's and are clipped to it, the rest only fades. A closed pill's buttons
 * ([isClosed]) are faded rather than cut off where they stand past it, since the two tabs' pills hold different buttons
 * (the song filters); an open field's text is cut off, never left hanging past a pill narrowing into a closed one. The
 * row is laid out at the bar's origin, as [SearchPillSurface] is.
 */
internal fun Modifier.searchPillForeground(
    chrome: SharedTabChrome?,
    bounds: () -> Rect,
    isClosed: () -> Boolean,
) = tabChromeForeground(
    chrome = chrome,
    zIndex = SEARCH_PILL_CONTENT_Z_INDEX,
    partBounds = bounds,
    isFadedPastTravelling = isClosed,
)
