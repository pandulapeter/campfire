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

/**
 * The groups of an open search: the sections run together under no header, so that the cards of one fill the columns the
 * one before it left open instead of each section ending in a short row. The Selection group loses its header too and
 * keeps the divider after it ([SongGroup.isSelection]), which is what tells its songs apart from the rest.
 *
 * Running the sections together moves every card after the first short row into another column, the one the eye is on
 * included. With [anchor], the group it is in starts with as many empty cells as keep its card in the column it was in
 * ([SongGroup.leadingGap]), so the cards around it close up on it rather than it moving; the gap is at the start of the
 * results, which is as far from where the list is read as it can be put.
 *
 * With no section to run together, the list is the one it was given, the same instance.
 */
internal fun List<SongGroup>.withoutSectionHeaders(anchor: SongCardSlot? = null): List<SongGroup> {
    if (none { it.header != null }) return this
    val selection = filter { it.isSelection }.map { it.copy(header = null) }
    val sections = filter { it.header is SongGroupHeader.Section }
    val merged = if (sections.isEmpty()) selection else selection + SongGroup(header = null, songs = sections.flatMap { it.songs })
    if (anchor == null) return merged
    return merged.map { group ->
        val position = group.indexOfItem(anchor.key)
        if (position < 0) group else group.copy(leadingGap = (anchor.column - position).mod(anchor.columnCount))
    }
}

/** The card with [key], in [column] of a grid of [columnCount] columns. */
internal data class SongCardSlot(val key: Any, val column: Int, val columnCount: Int)

/** Where the card with [key] is in a grid of [columnCount] columns showing these groups, or null if none shows it. */
internal fun List<SongGroup>.slotOf(key: Any, columnCount: Int): SongCardSlot? = firstNotNullOfOrNull { group ->
    group.indexOfItem(key).takeIf { it >= 0 }?.let { position -> SongCardSlot(key = key, column = (group.leadingGap + position) % columnCount, columnCount = columnCount) }
}
