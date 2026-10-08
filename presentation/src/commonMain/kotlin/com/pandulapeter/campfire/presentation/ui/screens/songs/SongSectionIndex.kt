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
    data class Group(val songCount: Int, val header: SongSection.Header?)

    private val ends = IntArray(groups.size)
    private val labels = ArrayList<String?>(groups.size)
    private val headersByKey = mutableMapOf<String, SongSection.Header>()

    init {
        var end = 0
        groups.forEachIndexed { index, group ->
            end += group.songCount + if (group.header == null) 0 else 1
            ends[index] = end
            labels += group.header?.fastScrollerLabel
            group.header?.let { headersByKey["header_${it.key}"] = it }
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

    fun headerForKey(key: Any): SongSection.Header? = headersByKey[key]
}

/**
 * The single character shown in the bubble of the fast scroller while this section is at the top of the list.
 */
private val SongSection.Header.fastScrollerLabel: String
    get() = when (this) {
        is SongSection.Header.Artist -> initial?.toString() ?: SYMBOLS_LABEL
        is SongSection.Header.Letter -> letter.toString()
        SongSection.Header.Symbols -> SYMBOLS_LABEL
    }

private const val SYMBOLS_LABEL = "#"
