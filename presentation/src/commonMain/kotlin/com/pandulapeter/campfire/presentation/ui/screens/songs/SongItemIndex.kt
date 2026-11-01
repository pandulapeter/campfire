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
        if (group.leadingGap > 0) index++
        val songIndex = group.indexOfItem(key)
        if (songIndex >= 0) return index + songIndex
        index += group.songs.size
        if (group.isRandomPicksQuestionAfter) index++
        if (hasDividerAfter(groupIndex)) index++
    }
    return null
}

/**
 * Whether [SongList] follows the group at [index] with the divider that sets a leading group, the Selection or the
 * random picks, off from the sections.
 */
internal fun List<SongGroup>.hasDividerAfter(index: Int) = (this[index].isSelection || this[index].isRandomPicks) && index < lastIndex

/**
 * The grid index of the first card, counted the way [SongList] emits its items: zero-height header slots are not scroll
 * distance, so the first card can be item 1 at the exact top. A group's [SongGroup.leadingGap] is the first item of the
 * row its first card is in, and is where that row starts.
 */
internal fun List<SongGroup>.firstCardIndex(hasPlaceholder: Boolean): Int {
    var index = if (hasPlaceholder) 1 else 0
    for (group in this) {
        if (group.header != null) index++
        if (group.songs.isNotEmpty()) break
    }
    return index
}

/**
 * The grid index of the header of the first section after the random picks that lead these groups, counted the way
 * [SongList] emits its items when no search is open: the picks' header, their songs, their question and the divider.
 * Null where the list does not start with the picks, or has nothing after them.
 */
internal fun List<SongGroup>.firstSectionAfterRandomPicksIndex(): Int? {
    val picks = firstOrNull()?.takeIf { it.isRandomPicks && size > 1 } ?: return null
    return 1 + picks.songs.size + (if (picks.isRandomPicksQuestionAfter) 1 else 0) + if (hasDividerAfter(0)) 1 else 0
}
