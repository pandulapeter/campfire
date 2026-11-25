/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails.dock

import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.presentation.ui.songView.model.RenderSection
import com.pandulapeter.campfire.presentation.ui.songView.model.SongScaleGroup

/**
 * The chord the dock's diagram strip scrolls to for a page whose first section is [section] (an index into
 * [sections]): the first of [names] (the strip's chords) played from there on, which is the page's first chord, else the
 * last one played before it, since a section of none (an instrumental's comment, a picture) is still played over the
 * chord before it. Null where the song plays none of them.
 */
internal fun firstChordFrom(sections: List<RenderSection>, section: Int, names: Set<String>): String? {
    fun chordsOf(index: Int) = (sections[index] as? RenderSection.Lines)?.lines.orEmpty().asSequence()
        .flatMap { line -> (line as? ChordProLine.Lyrics)?.chords.orEmpty() }
        .filter { !it.isAnnotation && !it.isMark && it.name in names }
        .map { it.name }
    val from = section.coerceIn(0, sections.size)
    return (from until sections.size).firstNotNullOfOrNull { chordsOf(it).firstOrNull() }
        ?: (from - 1 downTo 0).firstNotNullOfOrNull { chordsOf(it).lastOrNull() }
}

/**
 * The group of [groups] (by index) the dock's row of scales scrolls to for a page whose first section is [section]: the
 * one the last of its sections at or before it is in, since a section no group is for (a comment, a picture) is still
 * played in the group before it, else the first one the song comes to after it. Null where no group names a section.
 */
internal fun scaleGroupAt(groups: List<SongScaleGroup>, section: Int): Int? {
    val before = groups.indices.mapNotNull { index -> groups[index].sections.filter { it <= section }.maxOrNull()?.let { index to it } }
    if (before.isNotEmpty()) return before.maxBy { it.second }.first
    return groups.indices.mapNotNull { index -> groups[index].sections.minOrNull()?.let { index to it } }.minByOrNull { it.second }?.first
}
