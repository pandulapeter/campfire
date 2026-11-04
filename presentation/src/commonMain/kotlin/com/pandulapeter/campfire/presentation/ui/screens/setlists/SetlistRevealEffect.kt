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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import com.pandulapeter.campfire.presentation.ui.state.setlists.ListedSetlists
import com.pandulapeter.campfire.presentation.ui.state.setlists.SetlistsController
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Brings the setlist [SetlistsController.revealedSetlist] names to the top of the list, once [listedSetlists] (the list
 * as composed, not as the view model last worked it out) holds it unsearched: the search the reveal closed sends the list
 * to its top in the composition its unsearched list arrives in, and the list measures that a frame later, so the scroll
 * waits that frame out rather than being undone by it. A setlist the list never comes to hold (synced away meanwhile) is
 * given up on, leaving the list where it is.
 *
 * A screen only now arriving moves in whole, so it is not scrolled but started on the setlist where the list already
 * holds it: asked for in a side effect, which runs before the grid first measures. Called before the list itself, whose
 * own side effects then still have the last word over a search closed under it.
 */
@Composable
internal fun SetlistRevealEffect(
    listState: LazyGridState,
    listedSetlists: ListedSetlists,
    isPerformanceModeEnabled: Boolean,
    setlistsController: SetlistsController,
) {
    val reveal = setlistsController.revealedSetlist
    val currentSetlists by rememberUpdatedState(listedSetlists)
    val initialIndex = remember(reveal) {
        listedSetlists.takeIf { reveal?.isAnimated == false && it.filterKey.isEmpty() }?.setlists?.headerIndexOf(reveal?.fileName, isPerformanceModeEnabled)
    }
    SideEffect {
        if (initialIndex != null && listState.firstVisibleItemIndex != initialIndex) listState.requestScrollToItem(initialIndex)
    }
    LaunchedEffect(reveal) {
        if (reveal == null) return@LaunchedEffect
        try {
            val headerIndex = withTimeoutOrNull(REVEAL_TIMEOUT) {
                snapshotFlow {
                    currentSetlists.takeIf { it.filterKey.isEmpty() }?.setlists?.headerIndexOf(reveal.fileName, isPerformanceModeEnabled)
                }.filterNotNull().first()
            } ?: return@LaunchedEffect
            withFrameNanos { }
            if (reveal.isAnimated) listState.animateScrollToItem(headerIndex) else listState.scrollToItem(headerIndex)
        } finally {
            if (setlistsController.revealedSetlist == reveal) setlistsController.revealedSetlist = null
        }
    }
}

private val REVEAL_TIMEOUT = 5.seconds
