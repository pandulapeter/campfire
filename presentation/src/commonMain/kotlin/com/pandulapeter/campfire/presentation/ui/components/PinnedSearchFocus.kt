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

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import com.pandulapeter.campfire.presentation.ui.components.menu.LocalOverlayState
import com.pandulapeter.campfire.presentation.ui.platform.CompactKeyboardEffect
import kotlinx.coroutines.flow.collectLatest

/**
 * What a [pinnedSearchField] does while it is typed into, given the [Modifier] this returns: the keyboard is made compact
 * in a short window, and the field is pinned at the top of [gridState]'s page once per focus. The page is laid out under
 * the keyboard, which only pads the grid, so the field's bring-into-view counts what the keyboard covers as shown: pinned
 * at the top instead, what it finds is above the keyboard.
 */
@Composable
internal fun rememberPinnedSearchFocus(gridState: LazyGridState): Modifier {
    var isSearchFocused by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val ime = WindowInsets.ime
    // Derived, so that the page recomposes as the keyboard comes and goes rather than on every frame it slides.
    val isKeyboardVisible by remember(ime, density) { derivedStateOf { ime.getBottom(density) > 0 } }
    val isTypingSearch = isSearchFocused && isKeyboardVisible
    CompactKeyboardEffect(isEnabled = isTypingSearch && LocalWindowInfo.current.containerDpSize.height < SHORT_WINDOW_HEIGHT)
    // Read through a state: every platform but Android hands out a new, fixed instance on every composition (see
    // KeyboardAwarePadding), which a flow started once would never see move.
    val imeState = rememberUpdatedState(ime)
    LaunchedEffect(isTypingSearch) {
        if (isTypingSearch) {
            // It follows the keyboard as it rises, since the grid can only scroll as far as the padding added so far
            // allows.
            snapshotFlow { imeState.value.getBottom(density) }.collectLatest {
                gridState.animateScrollToItem(PINNED_SEARCH_INDEX)
            }
        }
    }
    return remember { Modifier.onFocusChanged { isSearchFocused = it.hasFocus } }
}

/**
 * Empties a tool page's search on a back gesture before the page is left, as an open search goes before a list screen,
 * while [query] holds something and nothing is over the page ([isDialogShown], a menu). The desktop never reaches this:
 * its window key handler sees Escape first, and empties the field there (`ScreenSearches.closeCurrentSearch`).
 */
@Composable
internal fun QueryBackHandler(
    query: String,
    isDialogShown: Boolean,
    onCleared: () -> Unit,
) = NavigationBackHandler(
    state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
    isBackEnabled = query.isNotEmpty() && !isDialogShown && !LocalOverlayState.current.isAnyMenuOpen,
    onBackCompleted = onCleared,
)
