/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView.grid

import com.pandulapeter.campfire.presentation.ui.songView.model.SongUnits

/**
 * The units of a song as lists of line widths in pixels, measured the way text is: every line a lyric line of
 * [LINE_HEIGHT] half tall for every row it wraps into at a width. A unit of [tabUnits] cannot be narrower than its
 * widest line, which does not wrap; any other one is as narrow as its longest word, a fixed fraction of its widest line.
 * Every height asked for is counted in [heightCalls], with its width.
 */
internal class FakeSectionMetrics(
    private val unitLines: List<List<Int>>,
    private val tabUnits: Set<Int> = emptySet(),
) : SectionMetrics {

    val heightCalls = mutableListOf<Pair<Int, Int>>()

    override fun height(index: Int, width: Int): Int {
        heightCalls += index to width
        return unitLines[index].sumOf { lineWidth -> (lineWidth + width - 1) / width.coerceAtLeast(1) } * LINE_HEIGHT / 2
    }

    override fun minWidth(index: Int) = maxWidth(index).let { if (index in tabUnits) it else it / LONGEST_WORD_SHARE }

    override fun maxWidth(index: Int) = unitLines[index].maxOrNull() ?: 0
}

/**
 * The units of a song of the sections of [sections], each a list of its units, each unit a list of its line widths,
 * a stretch of the song starting at every section of [timingStarts]; a section of [cardSections] is drawn on a card.
 */
internal fun songUnitsOf(
    sections: List<List<List<Int>>>,
    timingStarts: IntArray = intArrayOf(0),
    cardSections: Set<Int> = emptySet(),
): SongUnits {
    val units = sections.flatten()
    val sectionStarts = IntArray(sections.size + 1)
    sections.forEachIndexed { index, section -> sectionStarts[index + 1] = sectionStarts[index] + section.size }
    var cardCount = 0
    val cardStarts = IntArray(sections.size) { section ->
        if (section in cardSections) cardCount.also { cardCount += sections[section].size } else -1
    }
    return SongUnits(
        sectionStarts = sectionStarts,
        unitSections = unitSections(sections.map { section -> section.indices.toList() }),
        itemRanges = units.indices.map { it..it },
        isCuttableBefore = BooleanArray(units.size) { unit -> unit !in sectionStarts },
        lineCount = IntArray(units.size) { units[it].size },
        isStanzaBreakBefore = BooleanArray(units.size),
        cardStarts = cardStarts,
        cardCount = cardCount,
        timingStarts = timingStarts,
    )
}

/** The flat list of line widths of every unit of [sections], in the order [songUnitsOf] numbers them. */
internal fun unitLinesOf(sections: List<List<List<Int>>>) = sections.flatten()

/** A search with the screen's proportions: readable columns 384 to 560 wide, narrow ones down to 288. */
internal fun spec(
    availableHeight: Int = 2_000,
    maxRowHeight: Int = availableHeight,
    endInset: Int = 0,
    keepsEndInset: Boolean = false,
    canCutSections: Boolean = true,
    isSingleColumn: Boolean = false,
    cardPadding: Int = 0,
) = SongGridSpec(
    minColumnWidth = 384,
    narrowColumnWidth = 288,
    maxColumnWidth = 560,
    columnGap = SECTION_GAP,
    sectionGap = SECTION_GAP,
    rowGap = ROW_GAP,
    availableHeight = availableHeight,
    maxRowHeight = maxRowHeight,
    endInset = endInset,
    keepsEndInset = keepsEndInset,
    cardPadding = cardPadding,
    lyricsLineHeight = LINE_HEIGHT / 2,
    canCutSections = canCutSections,
    isSingleColumn = isSingleColumn,
)

private const val LONGEST_WORD_SHARE = 4
