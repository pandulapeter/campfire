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
import kotlin.test.Test
import kotlin.test.assertEquals

class SongSectionIndexTest {

    @Test
    fun matchesExpandedLabelsAtEveryBoundary() {
        val groups = listOf(
            SongSectionIndex.Group(0, SongSection.Header.Letter('A')),
            SongSectionIndex.Group(3, SongSection.Header.Letter('B')),
            SongSectionIndex.Group(1, SongSection.Header.Symbols),
            SongSectionIndex.Group(0, null),
            SongSectionIndex.Group(2, SongSection.Header.Artist("Zed", 'Z', "zed")),
        )
        val index = SongSectionIndex(groups)
        val expanded = groups.flatMap { group ->
            val label = when (val header = group.header) {
                is SongSection.Header.Letter -> header.letter.toString()
                is SongSection.Header.Artist -> header.initial?.toString() ?: "#"
                SongSection.Header.Symbols -> "#"
                null -> null
            }
            List(group.songCount + if (group.header == null) 0 else 1) { label }
        }
        for (position in -1..expanded.size) assertEquals(expanded.getOrNull(position), index.labelForItem(position))
        assertEquals(SongSection.Header.Letter('B'), index.headerForKey("header_B"))
        assertEquals(null, index.headerForKey("header_missing"))
    }

    @Test
    fun headerlessSearchAndEmptyGroupsHaveNoLabels() {
        assertEquals(null, SongSectionIndex(emptyList()).labelForItem(0))
        val index = SongSectionIndex(listOf(SongSectionIndex.Group(3, null)))
        for (position in 0..2) assertEquals(null, index.labelForItem(position))
    }
}
