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

import com.pandulapeter.campfire.presentation.ui.songView.grid.SectionSizesPool
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
 * and its items from [firstItem] to [lastItem], and the folds of the section, which are part of what it holds since they
 * change its height.
 */
internal data class UnitContent(
    val section: RenderSection,
    val firstItem: Int,
    val lastItem: Int,
    /** The keys of [foldedSections][FoldedRuns] that fold this section or a run inside it, see [foldedKeysOf]. */
    val foldedKeys: Set<String>,
)

/**
 * The chunks the sections of a song are composed and laid out as: every line of a section that may be cut, and every
 * other section whole. The chunks of section `s` are [sectionStarts]`[s]` to [sectionStarts]`[s + 1]`, each of them the
 * section's items in [itemRanges] and each belonging to the section in [unitSections], and the section may only be cut
 * in front of the ones [isCuttableBefore] says so of (see [sectionChunkStarts]). Every line is a chunk of its own, rather
 * than every stretch between two places a cut may fall, so that the layout knows where every line starts, which is
 * where a step through a section taller than the screen brings the next page to (see [SongRows.lineTops]).
 *
 * What a layout is judged by is in the chunks too. [lineCount] is how many lines of lyrics a chunk is, with or without
 * their chords: a line of a [RenderSection.Lines] is one, and an empty line, a comment, a key change, a piece of a run
 * of tablature or grid lines (which [RenderSection.Lines.itemKinds] tells apart as [SectionItemKind.RUN]), a row of
 * chord diagrams, the song's metadata and everything else between two sections are none, since a piece of a section
 * left with only one of those is no stranded line of the song. A section that is not cut is one chunk of all of its
 * lines. [isStanzaBreakBefore] says of a chunk whether a cut in front of it falls where the song's writer already
 * marked a boundary - after an empty line, or in front of a comment - which is never true of a section's first chunk,
 * since that is no cut of the section at all.
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
    val lineCount: IntArray,
    val isStanzaBreakBefore: BooleanArray,
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
            lineCount = lineCount.copyOfRange(unitFrom, unitUntil),
            isStanzaBreakBefore = isStanzaBreakBefore.copyOfRange(unitFrom, unitUntil),
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
            val lineCount = mutableListOf<Int>()
            val isStanzaBreakBefore = mutableListOf<Boolean>()
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
                val kinds = (section as? RenderSection.Lines)?.itemKinds
                ranges.forEach { range ->
                    unitSections += index
                    itemRanges += range
                    isCuttableBefore += range.first > 0 && cutStarts?.contains(range.first) == true
                    lineCount += kinds?.let { range.count { item -> it[item] == SectionItemKind.CONTENT } } ?: 0
                    isStanzaBreakBefore += kinds != null && range.first > 0 &&
                        (kinds[range.first - 1] == SectionItemKind.BLANK || kinds[range.first] == SectionItemKind.COMMENT)
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
                lineCount = lineCount.toIntArray(),
                isStanzaBreakBefore = isStanzaBreakBefore.toBooleanArray(),
                cardStarts = cardStarts,
                cardCount = cardCount,
                timingStarts = stretches.starts,
                timingSections = stretches.timingSections,
                isTimingSection = BooleanArray(sections.size) { sections[it] is RenderSection.Timing },
            )
        }
    }
}

/**
 * The key each of [sections] is emitted under in [SongLyrics]: its content, so a section inserted above the others does
 * not hand each of their nodes the section that used to be its neighbour, equal sections told apart by the order they
 * come in.
 */
internal fun sectionKeysOf(sections: List<RenderSection>): List<SectionKey> {
    val occurrences = HashMap<Any, Int>()
    return sections.map { section ->
        // The info section is the song's one header card: keyed by its content, every keystroke in a header directive
        // would compose it afresh, and whatever it remembers (the cover waiting for the typing to pause, the cover's
        // crossfade) would start over each time.
        val identity: Any = when (section) {
            is RenderSection.Metadata -> RenderSection.Metadata::class
            // Kept for the same reason: a shape chosen or the section folded is the same section, and folding it
            // has to fade its body in rather than compose the whole of it afresh.
            is RenderSection.Chords -> RenderSection.Chords::class
            else -> section
        }
        val occurrence = (occurrences[identity] ?: 0) + 1
        occurrences[identity] = occurrence
        SectionKey(hash = identity.hashCode(), occurrence = occurrence)
    }
}

/** The key of each chunk of [units], by its section's key in [sectionKeys]. */
internal fun unitKeysOf(units: SongUnits, sectionKeys: List<SectionKey>) =
    List(units.unitSections.size) { unit -> UnitKey(section = sectionKeys[units.unitSections[unit]], firstItem = units.itemRanges[unit].first) }

/** The key of each card of [units], as many for a section on a card as it could be cut into. */
internal fun cardKeysOf(units: SongUnits, sectionKeys: List<SectionKey>) = buildList {
    sectionKeys.indices.forEach { section ->
        if (units.cardStarts[section] >= 0) {
            repeat(units.sectionStarts[section + 1] - units.sectionStarts[section]) { piece -> add(CardKey(section = sectionKeys[section], piece = piece)) }
        }
    }
}
