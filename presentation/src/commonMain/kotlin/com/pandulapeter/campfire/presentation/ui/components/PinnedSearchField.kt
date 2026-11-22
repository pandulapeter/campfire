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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import com.pandulapeter.campfire.presentation.ui.components.appBar.LIST_APP_BAR_HEIGHT
import com.pandulapeter.campfire.presentation.ui.components.appBar.NEW_ITEM_BUTTON_END_INSET
import com.pandulapeter.campfire.presentation.ui.components.appBar.NEW_ITEM_BUTTON_GAP
import com.pandulapeter.campfire.presentation.ui.components.menu.NEW_ITEM_BUTTON_SIZE
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow

/**
 * The search of a tool's page (the Chords and the Scales tabs), under the controls and pinned at the top of the page
 * once the cells are scrolled under it, so that it can be used from anywhere down the page. The cells fade out under it
 * the way they fade out under a bar, by a gradient drawn below the field over what passes under it, as strong as they
 * have been scrolled under it; the page's own fade gives way to it as it arrives at the top, and the room above it,
 * under the status bar, is filled in with its background once it is pinned below that room (see
 * [pinnedSearchPageTopEdge] and [stickingBelowContentPadding]). It stands at [PINNED_SEARCH_INDEX], right after the
 * controls; the key and the field are no wider than [PINNED_SEARCH_MAX_WIDTH] together, at the start of a wide page.
 *
 * @param leading What the page is narrowed by besides the query, drawn before the field in its line and pinned with it:
 * the tabs' key, which the player changes as often as the query.
 */
internal fun LazyGridScope.pinnedSearchField(
    modifier: Modifier = Modifier,
    gridState: LazyGridState,
    query: String,
    placeholder: String,
    focusRequests: Flow<Unit>,
    onQueryChanged: (String) -> Unit,
    leading: @Composable () -> Unit,
) = stickyHeader(key = SEARCH_FIELD_KEY, contentType = SEARCH_FIELD_KEY) {
    val backgroundColor = MaterialTheme.colorScheme.background
    val fadeHeight = with(LocalDensity.current) { EDGE_FADE_SIZE.toPx() }
    Row(
        modifier = Modifier
            .stickingBelowContentPadding(gridState, PINNED_SEARCH_INDEX)
            .bleedHorizontally(TOOL_PAGE_PADDING)
            .fadingBelowPinnedHeader(backgroundColor, fadeHeight) {
                pinnedHeaderScrolledUnder(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset, PINNED_SEARCH_INDEX, fadeHeight)
            }
            .background(backgroundColor)
            .padding(bottom = 8.dp)
            .clearingFloatingPill(gridState),
        verticalAlignment = Alignment.Bottom,
    ) {
        // The line is narrowed rather than the row, whose background is what the cells pass under across the page.
        Row(
            modifier = Modifier.weight(1f, fill = false).widthIn(max = PINNED_SEARCH_MAX_WIDTH),
            verticalAlignment = Alignment.Bottom,
        ) {
            // The field keeps its own side padding, which already parts it from what comes before it.
            Box(modifier = Modifier.padding(start = 16.dp)) {
                leading()
            }
            SearchField(
                modifier = modifier.weight(1f),
                query = query,
                placeholder = placeholder,
                focusRequests = focusRequests,
                onQueryChange = onQueryChanged,
            )
        }
    }
}

/**
 * Narrows the field's line by the room the page's floating Preferences pill takes at its end, as far as the field has
 * come into its pinned place, where the pill stands on its line: none at rest, all of it once pinned, so that the field
 * gives way along the way the way a list's pinned header gives way to the app bar's buttons. Read while measuring, so
 * that a scroll recomposes nothing.
 */
private fun Modifier.clearingFloatingPill(gridState: LazyGridState) = layout { measurable, constraints ->
    val field = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == PINNED_SEARCH_INDEX }
    val pinnedFraction = when {
        gridState.firstVisibleItemIndex >= PINNED_SEARCH_INDEX -> 1f
        field == null -> 0f
        else -> (1f - field.offset.y / LIST_APP_BAR_HEIGHT.toPx()).coerceIn(0f, 1f)
    }
    val room = (FLOATING_PILL_ROOM.toPx() * pinnedFraction).roundToInt()
    val placeable = measurable.measure(constraints.offset(horizontal = -room))
    layout(constraints.constrainWidth(placeable.width + room), placeable.height) { placeable.placeRelative(0, 0) }
}

/**
 * What the field's line gives up at its end once pinned: the floating pill, one button wide on the New button's spot,
 * and the gap the list screens keep before that button, less the side padding the field keeps itself.
 */
private val FLOATING_PILL_ROOM = NEW_ITEM_BUTTON_END_INSET + NEW_ITEM_BUTTON_SIZE + NEW_ITEM_BUTTON_GAP - 16.dp

/** The search field's place in the page's grid, right after the controls. */
internal const val PINNED_SEARCH_INDEX = 1

/** Past this a query is a few letters at the start of a long empty field, far from the key before it. */
private val PINNED_SEARCH_MAX_WIDTH = 560.dp

private const val SEARCH_FIELD_KEY = "search"
