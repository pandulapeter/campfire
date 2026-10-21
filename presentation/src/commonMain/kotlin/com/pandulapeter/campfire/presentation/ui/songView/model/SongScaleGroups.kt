/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView.model

import androidx.compose.runtime.Immutable
import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.chordpro.chords.ChordProNotation
import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.chordpro.scales.Scale
import com.pandulapeter.campfire.chordpro.scales.Scales
import com.pandulapeter.campfire.chordpro.scales.SongScaleGroups
import com.pandulapeter.campfire.chordpro.scales.SongScaleSuggestions
import com.pandulapeter.campfire.presentation.ui.songLayout.DefaultSectionLabels
import com.pandulapeter.campfire.presentation.ui.songLayout.header
import com.pandulapeter.campfire.presentation.ui.songLayout.withNumberedSections

/**
 * One group of a song's Scales section: the sections it is for, named as the page heads them, and the scales they share.
 *
 * @property label The sections' headings, joined, or null for a song one group fits whole, which names nothing, and for
 *   a group of sections the page heads with nothing.
 * @property joinsSeveral Whether [label] joins more than one name, which a heading's own comma cannot tell.
 * @property hasUnnamed Whether the group also holds sections the page heads with nothing, besides the named ones.
 * @property opening The first words of a group with no named section, where more than one group has none, so that the
 *   two are told apart; null otherwise.
 * @property exceptions The chords of the group worth a scale of their own, each with its own row of chips.
 * @property scales The group's chips; none for the row of the chorded sections no group holds, which says so instead.
 * @property isHome Whether the group is what most of the song is played over, which no title names: the rows after it
 *   are its moments. False for a home named by its sections, where a row of sections no group holds follows it.
 */
@Immutable
internal data class SongScaleGroup(
    val label: String?,
    val rootName: String,
    val scales: List<Scale>,
    val exceptions: List<SongScaleException>,
    val joinsSeveral: Boolean,
    val hasUnnamed: Boolean,
    val opening: String?,
    val isHome: Boolean,
)

/**
 * The groups of scales [song] (in the standard notation) is played over ([SongScaleSuggestions]: the home first, named by
 * nothing, then the other keys and centres worth a change of scale; or, for a song in several keys with no home, every
 * group), each named by the headings of its sections in the order the song plays them, numbered the way the page
 * numbers them. Where a group holds every
 * section of a kind that plays a chord, and more than one, the kind is said once, at the first of them ("Verse" for
 * both verses); a lone one keeps its heading ("Take 5"), and the numbered ones are named where the verses are split
 * between groups ("Verse 3" for the one after a modulation). A heading another group, or no group, also has is numbered
 * by its occurrences among the song's chorded sections ("Verse (1, 2)" and "Verse (3)" where the page numbers nothing),
 * and several groups with no named section are told apart by their first words. The chorded sections no group holds
 * end the list as a row with no scales, named the same way (by its first words where it has no named section), and the
 * home is then named by its sections too, since it no longer covers the song. At most [MAX_GROUP_SCALES] scales a
 * group (the home scale, then what a player reaches for first), since a row of rarities buries the answer and the
 * Scales tab has every other one, and [MAX_EXCEPTION_SCALES] scale over each of its chords worth one, the common one
 * first, every chord named as the page shows it in [notation].
 */
internal fun songScaleGroupsOf(song: ChordProSong, labels: DefaultSectionLabels, notation: ChordNotation): List<SongScaleGroup> {
    val numbered = song.blocks.withNumberedSections(labels)
    val groups = SongScaleSuggestions.of(song.copy(blocks = numbered))
    if (groups.isEmpty()) return emptyList()
    val shownNames = ChordProNotation.shownNames(song, notation)
    val exceptions = groups.map { group ->
        group.exceptions.map { exception ->
            SongScaleException(
                chordNames = (listOf(exception.chordName) + exception.otherChordNames).map { name ->
                    ChordProNotation.withAccidentalSigns(shownNames[name] ?: name)
                },
                rootName = exception.rootName,
                scales = exception.scales.filter { it in Scales.common }.ifEmpty { exception.scales }.take(MAX_EXCEPTION_SCALES),
            )
        }
    }
    // A chorded section no group holds (a bridge no scale fits, a key past the moments, a part too short to be one) gets a
    // row of its own with no chips, so that the page never lets the home pass for the scale of it. The engine returns the
    // very blocks it was given, which is what makes identity the test.
    val missing = numbered.filter(SongScaleGroups::playsChords).filter { block -> groups.none { group -> group.blocks.any { it === block } } }
    // A song one group fits names nothing, and neither does a home, most of the song being played over it, unless parts
    // no group holds are named after it: then it covers only what its sections say.
    fun untitled(index: Int) = SongScaleGroup(
        label = null,
        rootName = groups[index].rootName,
        scales = chipsOf(groups[index].scales),
        exceptions = exceptions[index],
        joinsSeveral = false,
        hasUnnamed = false,
        opening = null,
        isHome = groups[index].isHome,
    )
    if (groups.size == 1 && missing.isEmpty()) return listOf(untitled(0))
    val played = numbered.filter(SongScaleGroups::playsChords).mapNotNull { block -> block.headingOf(labels)?.let { block to it } }
    val kindCounts = played.map { it.second }.filter { it.isNotEmpty() }.groupingBy(::kindOf).eachCount()
    val rowBlocks = groups.filterNot { it.isHome && missing.isEmpty() }.map { it.blocks } + listOf(missing).filter { it.isNotEmpty() }
    val headless = rowBlocks.count { blocks -> blocks.none { !it.headingOf(labels).isNullOrEmpty() } }

    fun rowOf(blocks: List<ChordProBlock>, rootName: String, scales: List<Scale>, exceptions: List<SongScaleException>): SongScaleGroup {
        val headers = blocks.mapNotNull { it.headingOf(labels) }
        val named = headers.filter { it.isNotEmpty() }
        val ofKind = named.groupingBy(::kindOf).eachCount()
        val names = named.map { header ->
            val kind = kindOf(header)
            if (ofKind.getValue(kind) > 1 && kindCounts[kind] == ofKind[kind]) kind else header.numberedAmong(played, blocks)
        }.distinct()
        // The missing row is told by its first words even where it is the only headless one: "the rest of the song" would
        // claim the parts a home titled "Chorus and the parts without a heading" covers.
        val isMissing = blocks === missing
        return SongScaleGroup(
            label = names.joinToString(", ").ifEmpty { null },
            rootName = rootName,
            scales = scales,
            exceptions = exceptions,
            joinsSeveral = names.size > 1,
            hasUnnamed = names.isNotEmpty() && headers.any { it.isEmpty() },
            opening = if (names.isEmpty() && (headless > 1 || isMissing)) blocks.firstOrNull()?.opening() else null,
            isHome = false,
        )
    }
    val rows = groups.mapIndexed { index, group ->
        if (group.isHome && missing.isEmpty()) untitled(index) else rowOf(group.blocks, group.rootName, chipsOf(group.scales), exceptions[index])
    }
    if (missing.isEmpty()) return rows
    return rows + rowOf(missing, groups.first().rootName, emptyList(), emptyList())
}

/** The heading the page gives a section or a recall, null for a continuation, which has none, and any other block. */
private fun ChordProBlock.headingOf(labels: DefaultSectionLabels) = when (this) {
    is ChordProBlock.Section -> takeIf { !it.isContinuation }?.header(labels)
    is ChordProBlock.ChorusRecall -> header(labels)
    else -> null
}

/**
 * This heading with the occurrences of it that [blocks] hold among the [played] headings, counted in play order
 * ("Verse (1, 2)"), where it is also played outside them; as it is where it is not.
 */
private fun String.numberedAmong(played: List<Pair<ChordProBlock, String>>, blocks: List<ChordProBlock>): String {
    val occurrences = played.filter { it.second == this }.map { (block, _) -> blocks.any { it === block } }
    if (occurrences.all { it }) return this
    return "$this (${occurrences.withIndex().filter { it.value }.joinToString(", ") { (it.index + 1).toString() }})"
}

/** The first words [this] sings, cut at a word to [OPENING_LENGTH] characters, or null where it sings none. */
private fun ChordProBlock.opening(): String? {
    val lines = when (this) {
        is ChordProBlock.Section -> lines
        is ChordProBlock.ChorusRecall -> blocks.filterIsInstance<ChordProBlock.Section>().flatMap { it.lines }
        else -> emptyList()
    }
    val lyric = lines.firstNotNullOfOrNull { line -> (line as? ChordProLine.Lyrics)?.text?.trim()?.takeIf { it.isNotEmpty() } } ?: return null
    if (lyric.length <= OPENING_LENGTH) return lyric
    val cut = lyric.take(OPENING_LENGTH)
    val end = if (lyric[OPENING_LENGTH].isWhitespace()) cut.length else cut.lastIndexOf(' ').takeIf { it > 0 } ?: cut.length
    return cut.take(end).trimEnd() + "…"
}

private const val OPENING_LENGTH = 32

/**
 * The chips of a group's [scales]: the eight-note ones left out where a smaller scale fits, since bebop and the
 * diminished scales are a jazz player's passing tones rather than the answer to what a song is in; after the first
 * two, the home scale whatever its rarity and the pentatonic inside it or a vamp's second textbook scale, only the
 * common scales and the hexatonics, since a Lydian ♯2 beside the major scale reads as a suggestion on a par with it;
 * then the first [MAX_GROUP_SCALES]. A chord sheet keeps them all.
 */
private fun chipsOf(scales: List<Scale>): List<Scale> {
    val shown = if (scales.any { it.intervals.size <= HOME_SCALE_SIZE }) scales.filter { it.intervals.size <= HOME_SCALE_SIZE } else scales
    return (shown.take(KEPT_CHIPS) + shown.drop(KEPT_CHIPS).filter { it in Scales.common || it.id in hexatonicIds }).take(MAX_GROUP_SCALES)
}

private const val KEPT_CHIPS = 2

private val hexatonicIds = setOf("major_hexatonic", "minor_hexatonic")

private const val HOME_SCALE_SIZE = 7

/**
 * A heading without the number the page counts its kind by ("Verse" for "Verse 2"): one after a space, as
 * `withNumberedSections` numbers them, so that "Chorus x2" is a heading of its own. A lone "Take 5" is of the kind
 * "Take" here and keeps its heading only through [songScaleGroupsOf]'s rule of saying a kind for more than one.
 */
private fun kindOf(header: String) = header.replace(KIND_NUMBER, "")

private val KIND_NUMBER = Regex("""\s+\d{1,4}$""")

internal const val MAX_GROUP_SCALES = 4
internal const val MAX_EXCEPTION_SCALES = 1
