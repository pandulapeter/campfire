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

import com.pandulapeter.campfire.domain.api.models.SongSection

/** Cumulative lazy-grid boundaries keep fast-scroll lookup and pushed-header lookup proportional to sections. */
internal class SongSectionIndex(groups: List<Group>) {
    /** @param itemCount The items of the group under its header: its songs, and the Selection section's divider. */
    data class Group(val itemCount: Int, val header: SongGroupHeader?)

    private val ends = IntArray(groups.size)
    private val labels = ArrayList<String?>(groups.size)
    private val headersByKey = mutableMapOf<String, SongGroupHeader>()

    init {
        var end = 0
        groups.forEachIndexed { index, group ->
            end += group.itemCount + if (group.header == null) 0 else 1
            ends[index] = end
            labels += group.header?.fastScrollerLabel
            group.header?.let { headersByKey[it.itemKey] = it }
        }
    }

    fun labelForItem(index: Int): String? {
        if (index < 0 || ends.isEmpty() || index >= ends.last()) return null
        var low = 0
        var high = ends.lastIndex
        while (low < high) {
            val middle = (low + high) / 2
            if (index < ends[middle]) high = middle else low = middle + 1
        }
        return labels[low]
    }

    fun headerForKey(key: Any): SongGroupHeader? = headersByKey[key]
}

/**
 * The single character shown in the bubble of the fast scroller while this section is at the top of the list.
 */
private val SongGroupHeader.fastScrollerLabel: String
    get() = when (this) {
        is SongGroupHeader.Section -> when (val header = header) {
            is SongSection.Header.Artist -> header.initial?.toString() ?: SYMBOLS_LABEL
            is SongSection.Header.Letter -> header.letter.toString()
            SongSection.Header.Symbols -> SYMBOLS_LABEL
        }

        SongGroupHeader.Selection -> SELECTION_LABEL
    }

private const val SYMBOLS_LABEL = "#"

/** The selection has no letter, and a check mark is what its boxes are ticked with. */
private const val SELECTION_LABEL = "\u2713"
