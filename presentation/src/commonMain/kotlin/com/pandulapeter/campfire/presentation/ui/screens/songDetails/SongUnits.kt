/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import androidx.compose.foundation.layout.size

/** The key a section is emitted under in [SongLyrics]: its content's hash, and which of the sections equal to it it is. */
internal data class SectionKey(
    val hash: Int,
    val occurrence: Int,
)

/** The key of a chunk of a section ([SongUnits]): its section's, and the first of the section's items it holds. */
internal data class UnitKey(
    val section: SectionKey,
    val firstItem: Int,
)

/** The key of the card a piece of a section is drawn on: its section's, and which of its pieces it is. */
internal data class CardKey(
    val section: SectionKey,
    val piece: Int,
)

/**
 * What a chunk of a section holds, which is what its measurements are reused by (see [SectionSizesPool]): the section
 * and its items from [firstItem] to [lastItem].
 */
internal data class UnitContent(
    val section: RenderSection,
    val firstItem: Int,
    val lastItem: Int,
)

/**
 * The chunks the sections of a song are composed and laid out as: every line of a section that may be cut, and every
 * other section whole. The chunks of section `s` are [sectionStarts]`[s]` to [sectionStarts]`[s + 1]`, each of them the
 * section's items in [itemRanges] and each belonging to the section in [unitSections], and the section may only be cut
 * in front of the ones [isCuttableBefore] says so of (see [sectionChunkStarts]). Every line is a chunk of its own, rather
 * than every stretch between two places a cut may fall, so that the layout knows where every line starts, which is
 * where a step through a section taller than the screen brings the next page to (see [SongRows.lineTops]).
 *
 * A section on a card is drawn on as many cards as it has chunks, since no more pieces of it can ever be placed than
 * that: its first is [cardStarts]`[s]` of the [cardCount] the layout is handed, -1 for a section drawn without a card.
 *
 * [timingStarts] are where the stretches of the song a change of tempo or time signature starts begin: 0, and the
 * section of every [RenderSection.Timing] some line of the song comes before. Each stretch is laid out as a song of its
 * own and starts a page, since the page being read is what tells the click how the song is played there (see
 * `SongSectionsLayout`). [timingSections] are the sections each change is in force from, and [isTimingSection] says of
 * every section whether it is one (see [timingStretchesOf]).
 */
internal class SongUnits(
    val sectionStarts: IntArray,
    val unitSections: IntArray,
    val itemRanges: List<IntRange>,
    val isCuttableBefore: BooleanArray,
    val cardStarts: IntArray,
    val cardCount: Int,
    val timingStarts: IntArray = intArrayOf(0),
    val timingSections: List<Int> = emptyList(),
    val isTimingSection: BooleanArray = BooleanArray(0),
) {

    /**
     * Whether the section at [section] is a change of tempo or time signature, which never shares a row: one that heads
     * the stretch it starts, and one written before the song's first line, which stands in place on the first page.
     */
    fun isTiming(section: Int) = isTimingSection.getOrElse(section) { false }

    /**
     * The sections from [from] until [until] as the units of a song of their own, every index counted from their first:
     * what a stretch of [timingStarts] is laid out as. The cards keep the numbers they have in the whole song.
     */
    fun slice(from: Int, until: Int): SongUnits {
        val unitFrom = sectionStarts[from]
        val unitUntil = sectionStarts[until]
        return SongUnits(
            sectionStarts = IntArray(until - from + 1) { sectionStarts[from + it] - unitFrom },
            unitSections = IntArray(unitUntil - unitFrom) { unitSections[unitFrom + it] - from },
            itemRanges = itemRanges.subList(unitFrom, unitUntil),
            isCuttableBefore = isCuttableBefore.copyOfRange(unitFrom, unitUntil),
            cardStarts = cardStarts.copyOfRange(from, until),
            cardCount = cardCount,
        )
    }

    companion object {

        /**
         * The chunks of [sections], where each section [isCuttable] says so is composed line by line, or a Chords section
         * row by row.
         */
        fun of(sections: List<RenderSection>, isCuttable: (RenderSection) -> Boolean): SongUnits {
            val sectionStarts = IntArray(sections.size + 1)
            val unitSections = mutableListOf<Int>()
            val itemRanges = mutableListOf<IntRange>()
            val isCuttableBefore = mutableListOf<Boolean>()
            val cardStarts = IntArray(sections.size) { -1 }
            var cardCount = 0
            sections.forEachIndexed { index, section ->
                val itemCount = when (section) {
                    is RenderSection.Lines -> section.itemCount
                    is RenderSection.Chords -> section.itemCount
                    else -> 1
                }
                val ranges = if (isCuttable(section) && itemCount > 1) List(itemCount) { item -> item..item } else listOf(0 until itemCount)
                val cutStarts = when (section) {
                    is RenderSection.Lines -> section.chunkStarts
                    is RenderSection.Chords -> section.chunkStarts
                    else -> null
                }
                ranges.forEach { range ->
                    unitSections += index
                    itemRanges += range
                    isCuttableBefore += range.first > 0 && cutStarts?.contains(range.first) == true
                }
                sectionStarts[index + 1] = sectionStarts[index] + ranges.size
                if (section is RenderSection.Lines && section.isOnCard) {
                    cardStarts[index] = cardCount
                    cardCount += ranges.size
                }
            }
            val stretches = timingStretchesOf(sections)
            return SongUnits(
                sectionStarts = sectionStarts,
                unitSections = unitSections.toIntArray(),
                itemRanges = itemRanges,
                isCuttableBefore = isCuttableBefore.toBooleanArray(),
                cardStarts = cardStarts,
                cardCount = cardCount,
                timingStarts = stretches.starts,
                timingSections = stretches.timingSections,
                isTimingSection = BooleanArray(sections.size) { sections[it] is RenderSection.Timing },
            )
        }
    }
}
