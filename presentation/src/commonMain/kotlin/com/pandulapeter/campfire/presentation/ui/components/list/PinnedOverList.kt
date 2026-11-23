/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.list

import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex

/**
 * Draws a lazy list's sticky item over the items scrolling under it, which it otherwise covers only where the list
 * happens to place it after them: Foundation gives a pinned item no drawing order of its own, and its grids place items
 * in no order that keeps it last. Without it the rows passing under a pinned search show through its background, and
 * cover the fade it draws below itself. Goes first on the item's outermost node, which is what the list orders.
 */
internal fun Modifier.pinnedOverList() = zIndex(PINNED_ITEM_Z_INDEX)

/** Above the items at the default 0, below a setlist's row being dragged, which the reorderable library lifts to 1. */
private const val PINNED_ITEM_Z_INDEX = 0.5f
