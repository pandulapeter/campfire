/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui

import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.ExperimentalFoundationApi

/**
 * Has every lazy grid prefetch the next line as it scrolls, as Foundation's grids did before its cache window became
 * their default. The cache window keeps lines composed around what is on screen, and in a grid with a sticky item
 * pinned at its top it asks for a line before the first one, throwing from the grid's span lookup in the middle of a
 * scroll (the tool pages' pinned search, scrolled with a mouse wheel). Set before any grid is composed, since a grid
 * reads it once, as it is first composed.
 */
@OptIn(ExperimentalFoundationApi::class)
internal fun prefetchLazyGridsWithoutCacheWindow() {
    ComposeFoundationFlags.isPreferDefaultCacheWindowOverPrefetchStrategyLazyGrid = false
}
