/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.importReport

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.components.EDGE_FADE_SIZE
import com.pandulapeter.campfire.presentation.ui.components.HideKeyboardWhenScrolledDown
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.platform.bounceScrollableContent

/**
 * The list both the question and the result are, centered and no wider than a line of text reads well at on a wide
 * window. It fades out under the bar rather than the bar lifting over it, like every list of the app - until the search
 * field at [searchFieldIndex] is pinned, which the fade would otherwise take the top of: from there the field is what
 * the rows fade under, see [searchField], and the list's own fade gives way to it as the field arrives at the top.
 */
@Composable
internal fun ReportList(
    contentPadding: PaddingValues,
    searchFieldIndex: Int?,
    content: LazyListScope.(LazyListState) -> Unit,
) {
    val listState = rememberLazyListState()
    val fadeHeight = with(LocalDensity.current) { EDGE_FADE_SIZE.roundToPx() }
    HideKeyboardWhenScrolledDown(listState)
    LazyColumn(
        modifier = Modifier.bounceScrollableContent(listState)
            .fillMaxSize()
            .fadingTopEdge(
                scrolled = {
                    val field = searchFieldIndex?.let { index -> listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } }
                    when {
                        searchFieldIndex != null && listState.firstVisibleItemIndex >= searchFieldIndex -> 0
                        // The fade is as strong as the field is far from the top, so it is gone by the time the field pins.
                        field != null && field.offset < fadeHeight -> field.offset.coerceAtLeast(0)
                        listState.firstVisibleItemIndex > 0 -> Int.MAX_VALUE
                        else -> listState.firstVisibleItemScrollOffset
                    }
                },
                backgroundColor = MaterialTheme.colorScheme.background,
            ),
        state = listState,
        contentPadding = contentPadding,
        horizontalAlignment = Alignment.CenterHorizontally,
        content = { content(listState) },
    )
}

/** The width every item of [ReportList] is laid out at, see there. */
internal val ItemWidth get() = Modifier.widthIn(max = LIST_MAX_WIDTH).fillMaxWidth()

private val LIST_MAX_WIDTH = 720.dp
