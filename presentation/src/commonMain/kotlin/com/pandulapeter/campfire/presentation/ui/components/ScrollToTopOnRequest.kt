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

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.pandulapeter.campfire.presentation.ui.components.list.animateScrollToTop
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.navigation.ScrollToTopRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

/**
 * Answers the [requests] for [destination], a tool tab's: scrolls [scrollState] to its top, or, where it is there
 * already, opens the request's `destinationWhenAtTop` through [onSelectDestination].
 */
@Composable
internal fun ScrollToTopOnRequest(
    requests: Flow<ScrollToTopRequest>,
    destination: CampfireDestination.TopLevel,
    scrollState: ScrollState,
    onSelectDestination: (CampfireDestination.TopLevel) -> Unit,
) = ScrollToTopOnRequest(
    requests = requests,
    destination = destination,
    key = scrollState,
    canScrollBackward = { scrollState.canScrollBackward },
    scrollToTop = { scrollState.animateScrollTo(0) },
    onSelectDestination = onSelectDestination,
)

/** [ScrollToTopOnRequest] for a lazy grid, scrolled to its first item. */
@Composable
internal fun ScrollToTopOnRequest(
    requests: Flow<ScrollToTopRequest>,
    destination: CampfireDestination.TopLevel,
    gridState: LazyGridState,
    onSelectDestination: (CampfireDestination.TopLevel) -> Unit,
) = ScrollToTopOnRequest(
    requests = requests,
    destination = destination,
    key = gridState,
    canScrollBackward = { gridState.canScrollBackward },
    scrollToTop = { gridState.animateScrollToTop() },
    onSelectDestination = onSelectDestination,
)

@Composable
private fun ScrollToTopOnRequest(
    requests: Flow<ScrollToTopRequest>,
    destination: CampfireDestination.TopLevel,
    key: Any,
    canScrollBackward: () -> Boolean,
    scrollToTop: suspend () -> Unit,
    onSelectDestination: (CampfireDestination.TopLevel) -> Unit,
) {
    val currentOnSelectDestination by rememberUpdatedState(onSelectDestination)
    LaunchedEffect(requests, key) {
        // Latest, for the reason the settings screen gives.
        requests.collectLatest {
            if (it.destination == destination) {
                if (canScrollBackward()) scrollToTop() else it.destinationWhenAtTop?.let(currentOnSelectDestination)
            }
        }
    }
}
