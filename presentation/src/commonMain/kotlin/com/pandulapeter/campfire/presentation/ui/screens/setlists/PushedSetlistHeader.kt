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

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_archive
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.AppBarOverlap
import com.pandulapeter.campfire.presentation.ui.components.SectionHeader
import com.pandulapeter.campfire.presentation.ui.components.SectionHeaderState
import com.pandulapeter.campfire.presentation.ui.components.SetlistActions
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.components.pushedSectionHeader
import com.pandulapeter.campfire.presentation.ui.components.pushedSectionHeaderPlacement
import com.pandulapeter.campfire.presentation.ui.components.rememberToday
import org.jetbrains.compose.resources.painterResource

/**
 * The outgoing setlist header, drawn over the grid while the next one pushes it up. A composable of its own for the
 * reason the songs screen's is: the push restarts nothing but it, and it only once per setlist.
 */
@Composable
internal fun PushedSetlistHeader(
    viewModel: CampfireViewModel,
    listState: LazyGridState,
    setlistsWithSongs: List<SetlistWithSongs>,
    endPadding: Dp,
    isPerformanceModeEnabled: Boolean,
    reorderingSetlistFileName: String?,
    appBarOverlap: () -> AppBarOverlap,
) {
    val pushed = pushedSectionHeader(listState, contentType = "setlist_header")
    val key by remember(pushed) { derivedStateOf { pushed.value?.key } }
    val setlistsByKey = remember(setlistsWithSongs) { setlistsWithSongs.associateBy { "setlist_${it.setlist.fileName}" } }
    val setlistWithSongs = key?.let { setlistsByKey[it] } ?: return
    val setlist = setlistWithSongs.setlist
    val today by rememberToday()
    SectionHeader(
        modifier = Modifier.pushedSectionHeaderPlacement(pushed).clearAndSetSemantics {},
        text = setlist.title,
        subtitle = setlistWithSongs.headerSubtitle(today),
        // Pinned for as long as it is being pushed away: it keeps the width it had in the bar's place rather than
        // widening again as it leaves.
        state = { SectionHeaderState(visibleFraction = pushed.value?.visibleFraction ?: 0f, pinnedFraction = 1f) },
        endPadding = endPadding,
        icon = if (setlist.isArchived) painterResource(Res.drawable.ic_archive) else null,
        onClick = null,
        action = if (isPerformanceModeEnabled) null else {
            { actionModifier, _ ->
                SetlistActions(
                    modifier = actionModifier,
                    viewModel = viewModel,
                    setlist = setlist,
                    isDecorative = true,
                    isReordering = reorderingSetlistFileName == setlist.fileName,
                    onReorder = if (setlistWithSongs.entries.size > 1) ({}) else null,
                )
            }
        },
        contentOpacity = { pushed.value?.visibleFraction ?: 0f },
        pushedDistancePx = { pushed.value?.pushedDistance ?: 0 },
        appBarOverlap = appBarOverlap,
    )
}
