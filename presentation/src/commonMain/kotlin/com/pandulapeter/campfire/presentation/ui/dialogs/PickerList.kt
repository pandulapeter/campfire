/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.components.HideKeyboardWhenScrolledDown
import com.pandulapeter.campfire.presentation.ui.components.ChecklistLayout
import com.pandulapeter.campfire.presentation.ui.components.KeepChecklistRowsInPlace
import com.pandulapeter.campfire.presentation.ui.components.ScrollToStartWhenChanged
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.components.listItemAnimation
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.platform.bounceScrollableContent

/**
 * The scrolling part of a picker sheet, which takes whatever height the sheet has left under its header rather than
 * the height of everything it could list - a library of songs is far taller than any screen.
 *
 * Its content never gets shorter while the sheet is open, only taller: narrowing the list by a search would otherwise
 * move the field being typed into down the screen along with it. The bottom inset is kept separately, so dismissing
 * the keyboard returns the sheet to its content's height instead of retaining empty space below the rows.
 *
 * @param contentPadding What the sheet's content keeps clear at the bottom, applied inside the scroll so that the last
 *   rows pass under the navigation bar and the keyboard on their way up.
 * @param refreshKey Search, sorting and filters; a change sends the list back to its first row once the
 *   refreshed [contents] arrive ([ScrollToStartWhenChanged]).
 * @param checklistLayout Where the rows of the checklist in the content start, which keeps them in place as its
 *   selected group grows ([KeepChecklistRowsInPlace]).
 * @param noResultsText What to say in place of the rows, null while there is nothing to say.
 * @param header What the list starts with and scrolls away with the rows, above what it says in their place, so that a
 *   filter that left nothing can still be turned off.
 * @param revealRowsKey Scrolls the [header] out of the way whenever it changes to something other than null - the
 *   search being typed, since the keyboard is up then and the rows it finds are what there is room for.
 */
@Composable
internal fun ColumnScope.PickerList(
    contentPadding: PaddingValues,
    refreshKey: Any?,
    contents: Any?,
    checklistLayout: ChecklistLayout,
    noResultsText: String?,
    revealRowsKey: Any? = null,
    header: (@Composable () -> Unit)? = null,
    content: LazyListScope.(LazyListState) -> Unit,
) {
    val listState = rememberLazyListState()
    val hasHeader = header != null
    LaunchedEffect(revealRowsKey, hasHeader) {
        if (revealRowsKey != null && hasHeader) {
            listState.scrollToItem(1)
        }
    }
    HideKeyboardWhenScrolledDown(listState)
    ScrollToStartWhenChanged(
        listState = listState,
        key = refreshKey,
        contents = contents,
    )
    KeepChecklistRowsInPlace(listState, checklistLayout)
    LazyColumn(
        modifier = Modifier.bounceScrollableContent(listState)
            .weight(1f, fill = false)
            .retainSheetContentHeight(contentPadding)
            // The rows fade out as they scroll up under the search field, which is the edge between the two
            // everywhere else in the app too.
            .fadingTopEdge(listState, sheetContainerColor())
            .followSheetGrowth(),
        state = listState,
        // The gap under the search field is the list's own content padding rather than a padding around the list, so
        // that a scrolled row goes under the field itself instead of being cut off a few pixels short of it.
        contentPadding = contentPadding.only(bottom = true, extraTop = PICKER_LIST_TOP_PADDING),
    ) {
        if (header != null) {
            item(key = "header") {
                Column(modifier = Modifier.padding(bottom = PICKER_LIST_TOP_PADDING)) {
                    header()
                }
            }
        }
        if (noResultsText != null) {
            item(key = "no_results") {
                Text(
                    modifier = listItemAnimation(listState).fillMaxWidth().padding(16.dp),
                    text = noResultsText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        content(listState)
    }
}

private val PICKER_LIST_TOP_PADDING = 8.dp
