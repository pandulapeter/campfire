/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.localization.textResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.scales
import com.pandulapeter.campfire.presentation.resources.song_details_scales_and_unnamed
import com.pandulapeter.campfire.presentation.resources.song_details_scales_and_unnamed_several
import com.pandulapeter.campfire.presentation.resources.song_details_scales_from
import com.pandulapeter.campfire.presentation.resources.song_details_scales_other
import com.pandulapeter.campfire.presentation.resources.song_details_scales_over
import com.pandulapeter.campfire.presentation.resources.song_details_scales_over_several
import com.pandulapeter.campfire.presentation.resources.song_details_scales_over_two
import com.pandulapeter.campfire.presentation.ui.components.controls.CHIP_GAP
import com.pandulapeter.campfire.presentation.ui.songView.model.FoldToggle
import com.pandulapeter.campfire.presentation.ui.songView.model.RenderSection
import com.pandulapeter.campfire.presentation.ui.songView.model.ScaleSuggestions
import com.pandulapeter.campfire.presentation.ui.songView.model.SongScaleException
import com.pandulapeter.campfire.presentation.ui.songView.model.SongScaleGroup

/**
 * One chunk of the scales the song is played over: the slots [items] names (counted as [RenderSection.Scales.itemCount]),
 * a section of its own after the chords whose first chunk is headed by a pill that folds it as the Chords section's does,
 * one choice for every song. A group of rows of chips per group of sections one set of scales fits: the home first,
 * titled by nothing, as most of the song is played over it, then each other key or centre named by its sections
 * ("Bridge 3"); a song in several keys with no home names every group ("Intro, Verse, Chorus" over C's, "Solo" over
 * D's), one of sections the page heads with nothing as the rest of the song, or by its first words where several are.
 * After each group a row per chord of it worth a scale of its own, titled by the chord ("Over E7") in every song, , each
 * line of chips a slot, as the Chords section's rows of diagrams are ([ScaleChipRows]), though the section is cut only
 * between rows, so that a title always heads all of its chips. A chip opens its scale's sheet.
 *
 * @param isFadingIn Whether the fold was toggled while the page is open, so that the rows coming back fade in, in every
 * chunk they come back in.
 * @param isAnimated Whether a chip a new width sends to another line travels there (see [ScaleChipRows]).
 */
@Composable
internal fun SongScalesSection(
    modifier: Modifier = Modifier,
    section: RenderSection.Scales,
    items: IntRange,
    scaleSuggestions: ScaleSuggestions?,
    isFadingIn: Boolean,
    isAnimated: Boolean,
    headerStyle: TextStyle,
    fontScale: Float,
) = Column(
    // The chips end flush with their last row, so the section's last chunk (its pill alone, folded) keeps a gap of its own
    // under it, for the section after it not to read as part of it.
    modifier = modifier.padding(bottom = if (items.last == section.itemCount - 1) SCALES_BOTTOM_GAP else 0.dp),
) {
    val isFirstChunk = items.first == 0
    if (isFirstChunk) {
        SectionHeaderPill(
            header = stringResource(Res.string.scales),
            toggle = scaleSuggestions?.let { suggestions ->
                FoldToggle(
                    isExpanded = !section.isFolded,
                    onToggled = suggestions.onFoldToggled,
                )
            },
            style = headerStyle,
            chevronSize = FOLD_CHEVRON_SIZE * fontScale,
        )
    }
    if (section.isFolded || scaleSuggestions == null) return@Column
    val isGrouped = section.groups.size > 1
    val other = stringResource(Res.string.song_details_scales_other)
    val density = LocalDensity.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fadingIn(isFadingIn = isFadingIn)
            .padding(top = if (isFirstChunk) SCALES_HEADER_GAP else 0.dp),
    ) {
        // The chips grow with the song's text as the Chords section's diagrams do: scaling the density they convert
        // their dp and sp with keeps their text, paddings, and gaps in the proportions Material drew them in.
        CompositionLocalProvider(
            LocalDensity provides Density(density = density.density * fontScale, fontScale = density.fontScale),
        ) {
            section.rows.forEachIndexed { index, row ->
                val rowStart = section.rowStarts[index]
                val slots = (maxOf(items.first, rowStart) - rowStart)..(minOf(items.last, section.rowStarts[index + 1] - 1) - rowStart)
                if (slots.isEmpty()) return@forEachIndexed
                val title = when {
                    slots.first != 0 -> null
                    row.exception != null -> row.exception.title()
                    isGrouped -> section.groups[row.groupIndex].takeIf { !it.isHome }?.title(other)
                    else -> null
                }
                ScaleChipRows(
                    modifier = Modifier.fillMaxWidth(),
                    title = title,
                    rootName = row.rootNameOf(section.groups),
                    scales = row.scalesOf(section.groups),
                    slots = slots,
                    trailingGap = when {
                        index == section.rows.lastIndex -> 0.dp
                        section.rows[index + 1].groupIndex == row.groupIndex -> CHIP_GAP
                        else -> 16.dp
                    },
                    notation = scaleSuggestions.notation,
                    isAnimated = isAnimated,
                    onScaleSelected = scaleSuggestions.onScaleClicked,
                )
            }
        }
    }
}

/**
 * What a row of chips over chords is titled: "Over E7", "Over F♯7 and F♯", or "Over A7, D7, and G7", the list said as
 * the language says one.
 */
@Composable
private fun SongScaleException.title(): String = when (chordNames.size) {
    1 -> stringResource(Res.string.song_details_scales_over, chordNames.single())
    2 -> stringResource(Res.string.song_details_scales_over_two, chordNames[0], chordNames[1])
    else -> stringResource(Res.string.song_details_scales_over_several, chordNames.dropLast(1).joinToString(", "), chordNames.last())
}

/**
 * What a group's row of chips is titled where the song has several: its sections' names, saying so where it also holds
 * parts without a heading, else its first words where several groups have no name, else the rest of the song, [other].
 */
@Composable
private fun SongScaleGroup.title(other: String): String {
    val label = label ?: return opening?.let { textResource(Res.string.song_details_scales_from, it) } ?: other
    return when {
        !hasUnnamed -> label
        joinsSeveral -> textResource(Res.string.song_details_scales_and_unnamed_several, label)
        else -> textResource(Res.string.song_details_scales_and_unnamed, label)
    }
}

private val SCALES_HEADER_GAP = 16.dp

private val SCALES_BOTTOM_GAP = 8.dp
