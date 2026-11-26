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

import com.pandulapeter.campfire.chordpro.chords.ChordProChordNames
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.GridToken
import com.pandulapeter.campfire.chordpro.tabs.ChordProTabWrapper
import com.pandulapeter.campfire.presentation.ui.songLayout.FoldableKind
import com.pandulapeter.campfire.presentation.ui.songView.SongPiece
import com.pandulapeter.campfire.presentation.ui.songView.model.RenderSection
import com.pandulapeter.campfire.presentation.ui.songView.model.SectionItem

/**
 * The chord a screen whose first line is [item] of [section] (indices into [sections] and that section's items) is
 * headed by, which the diagram strip marks where the screen plays none ([chordsOnScreen]): the first of [names] (the
 * strip's chords) played from there on, which is the screen's first chord, else the last one played before it, since a
 * stretch of none (an instrumental's comment, a picture) is still played over the chord before it. Null where the song
 * plays none of them.
 */
internal fun firstChordFrom(sections: List<RenderSection>, section: Int, item: Int, names: Set<String>): String? {
    val from = section.coerceIn(0, sections.size)
    val ahead = sequence {
        sections.getOrNull(from)?.let { yieldAll((item.coerceAtLeast(0) until it.itemCount).map { index -> from to index }) }
        for (next in from + 1 until sections.size) yieldAll((0 until sections[next].itemCount).map { index -> next to index })
    }
    val behind = sequence {
        sections.getOrNull(from)?.let { yieldAll((minOf(item, it.itemCount) - 1 downTo 0).map { index -> from to index }) }
        for (previous in from - 1 downTo 0) yieldAll((sections[previous].itemCount - 1 downTo 0).map { index -> previous to index })
    }
    return ahead.firstNotNullOfOrNull { (index, at) -> sections[index].chordsOf(at, names).firstOrNull() }
        ?: behind.firstNotNullOfOrNull { (index, at) -> sections[index].chordsOf(at, names).lastOrNull() }
}

/**
 * The chords of [names] (the strip's) that the lines of [onScreen] play, which the dock's diagram strip marks as the
 * page's: only the lines on it, not the rest of their sections, since a section the text size has made taller than the
 * screen is read a page at a time. A page of no chords (an instrumental's comment, a picture) marks the chord it is
 * headed by ([firstChordFrom]), so that one is always marked.
 */
internal fun chordsOnScreen(sections: List<RenderSection>, onScreen: List<SongPiece>, names: Set<String>): Set<String> {
    val played = onScreen.flatMapTo(mutableSetOf()) { piece ->
        sections.getOrNull(piece.section)?.let { section -> piece.items.asSequence().flatMap { section.chordsOf(it, names) } }.orEmpty().asIterable()
    }
    if (played.isNotEmpty()) return played
    val first = onScreen.minWithOrNull(compareBy({ it.section }, { it.items.first })) ?: return emptySet()
    return setOfNotNull(firstChordFrom(sections, first.section, first.items.first, names))
}

private val RenderSection.itemCount get() = (this as? RenderSection.Lines)?.itemCount ?: 0

/** The chords of [names] its [item] plays: a line's, a grid row's cells, and, for a slot of a tab, the whole run's. */
private fun RenderSection.chordsOf(item: Int, names: Set<String>): Sequence<String> {
    val lines = when (val sectionItem = (this as? RenderSection.Lines)?.sectionItems?.getOrNull(item)) {
        is SectionItem.Line -> sequenceOf(sectionItem.line)
        // A grid's slots are its rows, one each; a tab's are the rows its staves wrap into, which say nothing of where
        // its line of chord names is, so any of them stands for the whole run.
        is SectionItem.RunSlot -> if (sectionItem.kind == FoldableKind.GRID) {
            sectionItem.run.asSequence().filterIsInstance<ChordProLine.Grid>().drop(sectionItem.slot).take(1)
        } else {
            sectionItem.run.asSequence()
        }
        else -> emptySequence()
    }
    return lines.flatMap { it.chordNames() }.filter { it in names }
}

private fun ChordProLine.chordNames(): Sequence<String> = when (this) {
    is ChordProLine.Lyrics -> chords.asSequence().filter { !it.isAnnotation && !it.isMark }.map { it.name }
    is ChordProLine.Grid -> tokens.asSequence().filterIsInstance<GridToken.Chord>().flatMap { it.name.split(GRID_CELL_CHORD_SEPARATOR) }
    // A line of chord names over a tab's bars, which the page draws as chords.
    is ChordProLine.Tab -> if (ChordProTabWrapper.isChordLine(text)) {
        text.split(' ', '\t').asSequence().map { ChordProChordNames.withoutShapeMark(it).trim('(', ')') }
    } else {
        emptySequence()
    }
    else -> emptySequence()
}

private const val GRID_CELL_CHORD_SEPARATOR = '~'
