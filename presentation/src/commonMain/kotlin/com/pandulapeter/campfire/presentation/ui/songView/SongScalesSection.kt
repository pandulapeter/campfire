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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.scales
import com.pandulapeter.campfire.presentation.resources.song_details_scales_other
import com.pandulapeter.campfire.presentation.resources.song_details_scales_over
import com.pandulapeter.campfire.presentation.ui.components.controls.CHIP_GAP
import com.pandulapeter.campfire.presentation.ui.songView.model.FoldToggle
import com.pandulapeter.campfire.presentation.ui.songView.model.RenderSection
import com.pandulapeter.campfire.presentation.ui.songView.model.ScaleSuggestions

/**
 * One chunk of the scales the song is played over: the slots [items] names (counted as [RenderSection.Scales.itemCount]),
 * a section of its own after the chords whose first chunk is headed by a pill that folds it as the Chords section's does,
 * one choice for every song. A group of rows of chips per group of sections one set of scales fits, each named by those
 * sections where the song needs more than one ("Intro, Verse, Chorus" over C's, "Solo" over D's), a group of sections the
 * page heads with nothing named as the rest of the song, then a row per chord those scales leave out, titled by the chord
 * ("Over E7") in every song, and each line of chips a slot the section may be cut between, as the Chords section's rows
 * of diagrams are ([ScaleChipRows]). A chip opens its scale's sheet.
 *
 * @param isFadingIn Whether the fold was toggled while the page is open, so that the rows coming back fade in, in every
 * chunk they come back in.
 */
@Composable
internal fun SongScalesSection(
    modifier: Modifier = Modifier,
    section: RenderSection.Scales,
    items: IntRange,
    scaleSuggestions: ScaleSuggestions?,
    isFadingIn: Boolean,
    headerStyle: TextStyle,
    fontScale: Float,
) = Column(modifier = modifier) {
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fadingIn(isFadingIn = isFadingIn)
            .padding(top = if (isFirstChunk) HEADER_GAP + 4.dp else 0.dp),
    ) {
        section.rows.forEachIndexed { index, row ->
            val rowStart = section.rowStarts[index]
            val slots = (maxOf(items.first, rowStart) - rowStart)..(minOf(items.last, section.rowStarts[index + 1] - 1) - rowStart)
            if (slots.isEmpty()) return@forEachIndexed
            val title = when {
                slots.first != 0 -> null
                row.exception != null -> stringResource(Res.string.song_details_scales_over, row.exception.chordName)
                isGrouped -> section.groups[row.groupIndex].label ?: other
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
                onScaleSelected = scaleSuggestions.onScaleClicked,
            )
        }
    }
}
