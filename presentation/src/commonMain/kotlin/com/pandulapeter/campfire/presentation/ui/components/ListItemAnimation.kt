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

import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * Whether the lists have the whole library and can start animating their items, which they must not do while they
 * are still being filled.
 *
 * The first read of the library publishes what it has put together as it goes (see
 * `BaseLocalDataRepository.publishPartialData`), so a list arrives in several batches, each of which drops items in
 * among the ones already on screen and pushes the rest into other slots. Animating that turns the first moments of
 * the app into the library shuffling itself into shape, as if the layout kept changing its mind about where the
 * songs go.
 *
 * Hence the effect rather than `!isLoading` on its own: the last batch lands in the very composition [isLoading]
 * goes false in, so reading it directly would still animate the largest rearrangement of them all. Written from an
 * effect, the answer only changes in the composition after that, by which time nothing is moving any more.
 */
@Composable
internal fun rememberHasLoadedLibrary(isLoading: Boolean): Boolean {
    var hasLoadedLibrary by remember { mutableStateOf(false) }
    LaunchedEffect(isLoading) { if (!isLoading) hasLoadedLibrary = true }
    return hasLoadedLibrary
}

/**
 * The placement animation of an item of the song or setlist list, which it only has once the library is whole (see
 * [rememberHasLoadedLibrary]).
 *
 * An item sliding into its new slot says that the list the user is looking at has changed: a song was renamed,
 * deleted, filtered out, or a different sorting order moved it. The library arriving is not that kind of change.
 */
internal fun LazyGridItemScope.listItemAnimation(hasLoadedLibrary: Boolean) = if (hasLoadedLibrary) Modifier.animateItem() else Modifier
