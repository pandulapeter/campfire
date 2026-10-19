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
 */
@Immutable
internal data class SongScaleGroup(
    val label: String?,
    val rootName: String,
    val scales: List<Scale>,
)

/**
 * The groups of scales [song] (in the standard notation) is played over ([SongScaleGroups]), each named by the headings
 * of its sections in the order the song plays them, numbered the way the page numbers them. Where a group holds every
 * section of a kind, and more than one, the kind is said once, at the first of them ("Verse" for both verses); a lone
 * one keeps its heading ("Take 5"), and the numbered ones are named where the verses are split between groups ("Verse 3"
 * for the one after a modulation). At most [MAX_GROUP_SCALES] scales a group, the common ones first, since a long row of
 * rarities buries the answer.
 */
internal fun songScaleGroupsOf(song: ChordProSong, labels: DefaultSectionLabels): List<SongScaleGroup> {
    val groups = SongScaleGroups.of(song.copy(blocks = song.blocks.withNumberedSections(labels)))
    if (groups.size <= 1) return groups.map { SongScaleGroup(label = null, rootName = it.rootName, scales = it.scales.take(MAX_GROUP_SCALES)) }
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
            scales = group.scales.take(MAX_GROUP_SCALES),
        )
    }
}

/**
 * A heading without the number the page counts its kind by ("Verse" for "Verse 2"): one after a space, as
 * `withNumberedSections` numbers them, so that "Take 5" and "Chorus x2" are headings of their own.
 */
private fun kindOf(header: String) = header.replace(KIND_NUMBER, "")

private val KIND_NUMBER = Regex("""\s+\d{1,4}$""")

internal const val MAX_GROUP_SCALES = 5
