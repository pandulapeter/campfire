/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songs

/** The grid index of the item with [key], counted the way [SongList] emits its items. */
internal fun List<SongGroup>.itemIndexOf(key: Any, hasPlaceholder: Boolean): Int? {
    var index = if (hasPlaceholder) 1 else 0
    forEachIndexed { groupIndex, group ->
        if (group.header != null) index++
        val songIndex = group.indexOfItem(key)
        if (songIndex >= 0) return index + songIndex
        index += group.songs.size
        if (hasSelectionDividerAfter(groupIndex)) index++
    }
    return null
}

/** Whether [SongList] follows the group at [index] with the divider that sets the Selection section off from the rest. */
internal fun List<SongGroup>.hasSelectionDividerAfter(index: Int) = this[index].isSelection && index < lastIndex

/**
 * The grid index of the first card, counted the way [SongList] emits its items: zero-height header slots are not scroll
 * distance, so the first card can be item 1 at the exact top.
 */
internal fun List<SongGroup>.firstCardIndex(hasPlaceholder: Boolean): Int {
    var index = if (hasPlaceholder) 1 else 0
    for (group in this) {
        if (group.header != null) index++
        if (group.songs.isNotEmpty()) break
    }
    return index
}
