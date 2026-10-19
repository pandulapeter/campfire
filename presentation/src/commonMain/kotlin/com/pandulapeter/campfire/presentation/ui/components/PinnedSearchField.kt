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

import com.pandulapeter.campfire.presentation.ui.chrome.TOOL_PAGE_PADDING
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow

/**
 * The search of a tool's page (the Chords and the Scales tabs), under the controls and pinned at the top of the page
 * once the cells are scrolled under it, so that it can be used from anywhere down the page. The cells fade out under it
 * the way they fade out under a bar, by a gradient drawn below the field over what passes under it, as strong as they
 * have been scrolled under it; the page's own fade gives way to it as it arrives at the top (see [pinnedSearchTopFade]).
 * It stands at [PINNED_SEARCH_INDEX], right after the controls.
 */
internal fun LazyGridScope.pinnedSearchField(
    modifier: Modifier = Modifier,
    gridState: LazyGridState,
    query: String,
    placeholder: String,
    focusRequests: Flow<Unit>,
    onQueryChanged: (String) -> Unit,
) = stickyHeader(key = SEARCH_FIELD_KEY, contentType = SEARCH_FIELD_KEY) {
    val backgroundColor = MaterialTheme.colorScheme.background
    val fadeHeight = with(LocalDensity.current) { EDGE_FADE_SIZE.toPx() }
    Box(
        modifier = Modifier
            .bleedHorizontally(TOOL_PAGE_PADDING)
            .drawWithContent {
                drawContent()
                val scrolledUnder = when {
                    gridState.firstVisibleItemIndex > PINNED_SEARCH_INDEX -> fadeHeight
                    gridState.firstVisibleItemIndex == PINNED_SEARCH_INDEX -> gridState.firstVisibleItemScrollOffset.toFloat()
                    else -> 0f
                }
                val strength = (scrolledUnder / fadeHeight).coerceIn(0f, 1f)
                if (strength > 0f) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(backgroundColor, backgroundColor.copy(alpha = 0f)),
                            startY = size.height,
                            endY = size.height + fadeHeight,
                        ),
                        topLeft = Offset(0f, size.height),
                        size = Size(size.width, fadeHeight),
                        alpha = strength,
                    )
                }
            }
            .background(backgroundColor)
            .padding(bottom = 8.dp),
    ) {
        SearchField(
            modifier = modifier,
            query = query,
            placeholder = placeholder,
            focusRequests = focusRequests,
            onQueryChange = onQueryChanged,
        )
    }
}

/**
 * How far the page's top fade is to go for the content [gridState] scrolls: none once the search field is pinned, which
 * the fade would otherwise take the top of, as strong as the field is far from the top while it arrives, so that it is
 * gone by the time the field pins, and as far as the page has been scrolled before that.
 */
internal fun pinnedSearchTopFade(gridState: LazyGridState, fadeHeight: Int): Int {
    if (gridState.firstVisibleItemIndex >= PINNED_SEARCH_INDEX) return 0
    val field = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == PINNED_SEARCH_INDEX }
    return when {
        field != null && field.offset.y < fadeHeight -> field.offset.y.coerceAtLeast(0)
        gridState.firstVisibleItemIndex > 0 -> Int.MAX_VALUE
        else -> gridState.firstVisibleItemScrollOffset
    }
}

/** The search field's place in the page's grid, right after the controls. */
internal const val PINNED_SEARCH_INDEX = 1

private const val SEARCH_FIELD_KEY = "search"
