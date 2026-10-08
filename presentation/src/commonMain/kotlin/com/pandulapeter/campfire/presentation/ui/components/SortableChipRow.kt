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

import androidx.compose.animation.animateBounds
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.unit.Dp
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.ui.platform.bounceHorizontalScroll

/**
 * A group of filter chips on one row that scrolls sideways, with its [LabelSortingToggle] pinned at the start of the
 * row in place of a section title: the chips scroll behind it and fade out before they reach it
 * ([fadingUnderStartOverlay]), so the toggle stays where it is, read against the background, however far the row is
 * scrolled. Kept as small as it is next to a section title, with no touch target around it, so that the row is as tall
 * as its chips.
 *
 * A new order sends the row back to its start, which is where the order is read from, and every chip travels to its
 * new place (`animateBounds`) rather than staying put and being handed another label. That is why the row is not lazy:
 * a lazy row sent back to its start lets the placement animation of its items run for a single frame and then drops
 * them where they land, and the row only holds the library's tags and languages, which the songs screen's filters
 * compose all of too. The [LookaheadScope] is inside the scroll, so that the scroll moves the chips as a whole and only
 * the change of order is animated.
 */
@Composable
internal fun <T : Any> SortableChipRow(
    modifier: Modifier = Modifier,
    items: List<T>,
    key: (T) -> String,
    order: ChecklistOrder,
    refreshKey: Any?,
    sortingMode: UserPreferences.LabelSortingMode,
    onSortingModeSelected: (UserPreferences.LabelSortingMode) -> Unit,
    chip: @Composable (T) -> Unit,
) = Box(
    modifier = modifier.fillMaxWidth(),
    contentAlignment = Alignment.CenterStart,
) {
    val scrollState = rememberScrollState()
    val orderedItems = remember(items, order) { order.ordered(items, key) }
    val leadingCount = order.leadingCount(orderedItems, key)
    val scrollRefreshKey = sortingMode to refreshKey
    // Opening/restoring the row and selecting a chip leave its scroll position alone; refreshing animates to the start.
    var scrolledToStartFor by remember { mutableStateOf(scrollRefreshKey) }
    LaunchedEffect(scrollRefreshKey) {
        if (scrollRefreshKey != scrolledToStartFor) {
            scrolledToStartFor = scrollRefreshKey
            scrollState.animateScrollTo(0)
        }
    }
    // The icon itself starts at the keyline the search field above the row starts at.
    val toggleStart = CONTROLS_PADDING - (SORTING_TOGGLE_SIZE - SORTING_TOGGLE_ICON_SIZE) / 2
    val toggleEnd = toggleStart + SORTING_TOGGLE_SIZE
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fadingUnderStartOverlay(scrolledFromStart = { scrollState.value }, overlayWidth = toggleEnd)
            .bounceHorizontalScroll(scrollState),
    ) {
        LookaheadScope {
            Row(
                modifier = Modifier.padding(start = toggleEnd + SORTING_TOGGLE_GAP, end = CONTROLS_PADDING),
                horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                orderedItems.forEachIndexed { index, item ->
                    if (index == leadingCount && leadingCount > 0) {
                        VerticalDivider(modifier = Modifier.height(FilterChipDefaults.Height))
                    }
                    key(key(item)) {
                        Box(modifier = Modifier.animateBounds(this@LookaheadScope)) { chip(item) }
                    }
                }
            }
        }
    }
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        LabelSortingToggle(
            modifier = Modifier.padding(start = toggleStart).size(SORTING_TOGGLE_SIZE),
            sortingMode = sortingMode,
            onSortingModeSelected = onSortingModeSelected,
        )
    }
}
