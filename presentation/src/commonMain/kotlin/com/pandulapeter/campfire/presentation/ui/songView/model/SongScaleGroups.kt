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
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.chordpro.scales.Scale
import com.pandulapeter.campfire.chordpro.scales.SongScaleGroups
import com.pandulapeter.campfire.presentation.ui.songLayout.DefaultSectionLabels
import com.pandulapeter.campfire.presentation.ui.songLayout.header
import com.pandulapeter.campfire.presentation.ui.songLayout.withNumberedSections

/**
 * One group of a song's Scales section: the sections it is for, named as the page heads them, and the scales they share.
 *
 * @property label The sections' headings, joined, or null for a song one group fits whole, which names nothing, and for
 *   a group of sections the page heads with nothing.
 * @property exceptions The chords [scales] leave out, each with its own row of chips.
 */
@Immutable
internal data class SongScaleGroup(
    val label: String?,
    val rootName: String,
    val scales: List<Scale>,
    val exceptions: List<SongScaleException>,
)

/**
 * The groups of scales [song] (in the standard notation) is played over ([SongScaleGroups]), each named by the headings
 * of its sections in the order the song plays them, numbered the way the page numbers them. Where a group holds every
 * section of a kind, and more than one, the kind is said once, at the first of them ("Verse" for both verses); a lone
 * one keeps its heading ("Take 5"), and the numbered ones are named where the verses are split between groups ("Verse 3"
 * for the one after a modulation). At most [MAX_GROUP_SCALES] scales a group, the common ones first, since a long row of
 * rarities buries the answer, and [MAX_GROUP_EXCEPTIONS] chords it leaves out, the most played, [MAX_EXCEPTION_SCALES]
 * scales each, every chord named as the page shows it in [notation].
 */
internal fun songScaleGroupsOf(song: ChordProSong, labels: DefaultSectionLabels, notation: ChordNotation): List<SongScaleGroup> {
    val groups = SongScaleGroups.of(song.copy(blocks = song.blocks.withNumberedSections(labels)))
    if (groups.isEmpty()) return emptyList()
    val shownNames = ChordProNotation.shownNames(song, notation)
    val exceptions = groups.map { group ->
        group.exceptions.take(MAX_GROUP_EXCEPTIONS).map { exception ->
            SongScaleException(
                chordName = ChordProNotation.withAccidentalSigns(shownNames[exception.chordName] ?: exception.chordName),
                rootName = exception.rootName,
                scales = exception.scales.take(MAX_EXCEPTION_SCALES),
            )
        }
    }
    if (groups.size == 1) {
        return listOf(SongScaleGroup(label = null, rootName = groups.single().rootName, scales = chipsOf(groups.single().scales), exceptions = exceptions.single()))
    }
    val headers = groups.map { group -> group.sections.filter { !it.isContinuation }.map { it.header(labels) }.filter { it.isNotEmpty() } }
    val kindCounts = headers.flatten().groupingBy(::kindOf).eachCount()
    return groups.mapIndexed { index, group ->
        val ofKind = headers[index].groupingBy(::kindOf).eachCount()
        val names = headers[index].map { header ->
            val kind = kindOf(header)
            if (ofKind.getValue(kind) > 1 && kindCounts[kind] == ofKind[kind]) kind else header
        }
        SongScaleGroup(
            label = names.distinct().joinToString(", ").ifEmpty { null },
            rootName = group.rootName,
            scales = chipsOf(group.scales),
            exceptions = exceptions[index],
        )
    }
}

/**
 * The chips of a group's [scales]: the eight-note ones left out where a smaller scale fits, since bebop and the
 * diminished scales are a jazz player's passing tones rather than the answer to what a song is in, then the first
 * [MAX_GROUP_SCALES]. A chord sheet keeps them.
 */
private fun chipsOf(scales: List<Scale>) =
    (if (scales.any { it.intervals.size <= HOME_SCALE_SIZE }) scales.filter { it.intervals.size <= HOME_SCALE_SIZE } else scales).take(MAX_GROUP_SCALES)

private const val HOME_SCALE_SIZE = 7

/**
 * A heading without the number the page counts its kind by ("Verse" for "Verse 2"): one after a space, as
 * `withNumberedSections` numbers them, so that "Chorus x2" is a heading of its own. A lone "Take 5" is of the kind
 * "Take" here and keeps its heading only through [songScaleGroupsOf]'s rule of saying a kind for more than one.
 */
private fun kindOf(header: String) = header.replace(KIND_NUMBER, "")

private val KIND_NUMBER = Regex("""\s+\d{1,4}$""")

internal const val MAX_GROUP_SCALES = 5
internal const val MAX_GROUP_EXCEPTIONS = 3
internal const val MAX_EXCEPTION_SCALES = 3
