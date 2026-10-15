/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.songFilters

/**
 * The chips of a filter group a search leaves on screen: every one whose [searchKeys] contain [normalizedQuery], in the
 * order they came in, and every selected one whatever it is called, since a filter that is on has to stay visible to
 * be turned off - the rule the fold of a group keeps too. A query that folds to nothing searches nothing.
 *
 * @param normalizedQuery The query as `SongRenderer.normalizeForSearch` folds it, the way [searchKeys] are folded.
 */
internal fun <T> chipsMatching(
    items: List<T>,
    normalizedQuery: String,
    isSelected: (T) -> Boolean,
    searchKeys: (T) -> List<String>,
): List<T> = if (normalizedQuery.isEmpty()) {
    items
} else {
    items.filter { item -> isSelected(item) || searchKeys(item).any { normalizedQuery in it } }
}
