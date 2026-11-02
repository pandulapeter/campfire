/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.setlists

import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_archive
import com.pandulapeter.campfire.presentation.resources.setlists_archived
import com.pandulapeter.campfire.presentation.ui.components.actions.SetlistActionHandler
import com.pandulapeter.campfire.presentation.ui.components.actions.SetlistActions
import com.pandulapeter.campfire.presentation.ui.components.appBar.AppBarOverlap
import com.pandulapeter.campfire.presentation.ui.components.list.SectionHeader
import com.pandulapeter.campfire.presentation.ui.components.list.listItemAnimation
import com.pandulapeter.campfire.presentation.ui.components.list.rememberSectionHeaderState
import com.pandulapeter.campfire.presentation.ui.state.setlists.SetlistWithSongs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

/** The pinned header of a setlist in [SetlistList], with the setlist's menu outside performance mode. */
internal fun LazyGridScope.setlistHeader(
    setlistWithSongs: SetlistWithSongs,
    listState: LazyGridState,
    hasLoadedLibrary: Boolean,
    isReordering: Boolean,
    headerEndPadding: () -> Dp,
    coroutineScope: CoroutineScope,
    isPerformanceModeEnabled: Boolean,
    setlistActions: SetlistActionHandler,
    reorderingSetlistFileName: String?,
    onReorderingSetlistChanged: (String?) -> Unit,
    appBarOverlap: () -> AppBarOverlap,
) = stickyHeader(
    key = SetlistItemKey.header(setlistWithSongs.setlist.fileName),
    contentType = SetlistItemKey.HEADER_CONTENT_TYPE,
) { headerIndex ->
    val headerState = rememberSectionHeaderState(listState, headerIndex)
    SectionHeader(
        // Sticky placement belongs to the grid. Animating it as a regular item can briefly
        // pull an already-pinned header away when entering reorder mode changes the list.
        // Keep only its appearance fades.
        modifier = listItemAnimation(listState, hasLoadedLibrary, placementSpec = null),
        state = { headerState.value },
        // The card fade and animated placement can settle on different frames near the top.
        // Keep the pinned row opaque throughout reordering.
        backgroundColor = if (isReordering) MaterialTheme.colorScheme.background else Color.Transparent,
        endPadding = headerEndPadding,
        text = setlistWithSongs.setlist.title,
        // A setlist is only ever on this screen archived because the filter was asked to show them,
        // so the mark is what tells it from the ones still in use.
        icon = if (setlistWithSongs.setlist.isArchived) painterResource(Res.drawable.ic_archive) else null,
        iconContentDescription = stringResource(Res.string.setlists_archived),
        onClick = { coroutineScope.launch { listState.animateScrollToItem(headerIndex) } },
        action = if (isPerformanceModeEnabled) null else {
            { actionModifier, buttonModifier ->
                SetlistActions(
                    modifier = actionModifier,
                    buttonModifier = buttonModifier,
                    actions = setlistActions,
                    setlist = setlistWithSongs.setlist,
                    isReordering = isReordering && reorderingSetlistFileName == setlistWithSongs.setlist.fileName,
                    onReorder = if (setlistWithSongs.entries.size > 1) {
                        {
                            onReorderingSetlistChanged(
                                if (reorderingSetlistFileName == setlistWithSongs.setlist.fileName) null
                                else setlistWithSongs.setlist.fileName,
                            )
                        }
                    } else null,
                )
            }
        },
        opacity = { if (headerState.value.visibleFraction < 1f) 0f else 1f },
        appBarOverlap = appBarOverlap,
    )
}
