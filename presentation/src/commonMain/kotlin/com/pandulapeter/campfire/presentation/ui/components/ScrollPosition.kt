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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/**
 * Where the list of a top level screen was left, held outside the composition that draws it.
 *
 * Navigation 3 keeps what an entry remembers for exactly as long as the entry is on the back stack, and selecting a
 * tab rebuilds the stack around the destination that was picked. The songs screen sits at the bottom of every one
 * of those stacks and so is the only screen whose own `rememberSaveable` state survives a trip to another tab; the
 * setlists and the settings screens are thrown away as they are left. All three therefore hand their position to
 * the view model, which outlives the back stack entirely.
 */
internal class ScrollPosition {

    var index = 0
    var offset = 0
}

/**
 * A [LazyGridState] that starts where [position] was left and writes it back as it leaves the composition, which is
 * where the position is read rather than at every scrolled pixel: nothing but the value it has when the screen is
 * gone is ever restored from it.
 */
@Composable
internal fun rememberRetainedLazyGridState(position: ScrollPosition): LazyGridState {
    val state = rememberLazyGridState(
        initialFirstVisibleItemIndex = position.index,
        initialFirstVisibleItemScrollOffset = position.offset,
    )
    DisposableEffect(state, position) {
        onDispose {
            position.index = state.firstVisibleItemIndex
            position.offset = state.firstVisibleItemScrollOffset
        }
    }
    return state
}

/**
 * The [ScrollState] counterpart of the [rememberRetainedLazyGridState] above, for a screen that scrolls a layout
 * rather than a list and so has an offset but no items to count: the settings screen.
 */
@Composable
internal fun rememberRetainedScrollState(position: ScrollPosition): ScrollState {
    val state = rememberScrollState(initial = position.offset)
    DisposableEffect(state, position) {
        onDispose { position.offset = state.value }
    }
    return state
}
