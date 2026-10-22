/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.export

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.chordpro.scales.Scale
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.song_details_scales_other
import com.pandulapeter.campfire.presentation.resources.song_details_scales_unheld
import com.pandulapeter.campfire.presentation.ui.print.PrintScaleRow
import com.pandulapeter.campfire.presentation.ui.print.PrintSource
import com.pandulapeter.campfire.presentation.ui.scales.scaleTitle
import com.pandulapeter.campfire.presentation.ui.songView.model.SongScaleGroup
import com.pandulapeter.campfire.presentation.ui.songView.title

/**
 * [source] with each song's suggested scales put in words where they are printed ([isShown]), in the app's language and
 * the reader's [notation], each row titled as the song's Scales section titles it: the words are resources the layout,
 * which runs off the composition, cannot read. The same instance while the words are, so that a layout already made from
 * it stays current.
 */
@Composable
internal fun rememberPrintScales(
    source: PrintSource?,
    isShown: Boolean,
    notation: ChordNotation,
): PrintSource? {
    val other = stringResource(Res.string.song_details_scales_other)
    val unheld = stringResource(Res.string.song_details_scales_unheld)
    val rows = if (source == null || !isShown) null else source.songs.map { song -> printScaleRowsOf(song.scaleGroups, notation, other, unheld) }
    return remember(source, rows) {
        if (source == null || rows == null) source else source.copy(songs = source.songs.mapIndexed { index, song -> song.copy(scales = rows[index]) })
    }
}

/**
 * A line per group of [groups], the home's untitled, as most of the song is played over it, and a line per chord of a
 * group worth a scale of its own; a group no scale fits says so ([unheld]).
 */
@Composable
private fun printScaleRowsOf(
    groups: List<SongScaleGroup>,
    notation: ChordNotation,
    other: String,
    unheld: String,
): List<PrintScaleRow> = groups.flatMap { group ->
    val groupRow = PrintScaleRow(
        title = if (groups.size > 1 && !group.isHome) group.title(other) else null,
        text = if (group.scales.isEmpty()) unheld else scalesText(group.scales, group.rootName, notation),
    )
    listOf(groupRow) + group.exceptions.map { exception ->
        PrintScaleRow(title = exception.title(), text = scalesText(exception.scales, exception.rootName, notation))
    }
}

@Composable
private fun scalesText(scales: List<Scale>, rootName: String, notation: ChordNotation) = scales.map { scaleTitle(it, rootName, notation) }.joinToString(SCALE_SEPARATOR)

private const val SCALE_SEPARATOR = " · "
