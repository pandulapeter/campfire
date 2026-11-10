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

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified
import com.pandulapeter.campfire.abc.api.AbcSystem
import com.pandulapeter.campfire.presentation.ui.notation.NotationPainting
import com.pandulapeter.campfire.presentation.ui.notation.NotationPartChoices
import com.pandulapeter.campfire.presentation.ui.notation.SongNotations
import com.pandulapeter.campfire.presentation.ui.notation.notationPageWidth
import com.pandulapeter.campfire.presentation.ui.notation.rememberSongNotations
import com.pandulapeter.campfire.presentation.ui.songView.model.SongLyricsModel

/**
 * The staff notation of the song's ABC tunes, which replaces their text once it has been rendered: for a page as wide as
 * the column the layout places it in, first the narrowest column the song can have here ([NARROW_COLUMN_WIDTH]), asking
 * for the widest ([MAX_COLUMN_WIDTH]), each SVG unit as large as the text size makes it. Its
 * words are laid out in the lyrics' typeface at the sizes abc2svg asks for, so the style they start from is not scaled.
 *
 * @param hasPartChoices Whether a score is engraved one part at a time; not in the editor's preview, which is there to
 * show what is being written.
 */
@Composable
internal fun rememberSongLyricsNotations(
    model: SongLyricsModel,
    availableWidth: Dp,
    fontScale: Float,
    hasPartChoices: Boolean,
): SongNotations {
    val density = LocalDensity.current
    val localTextStyle = LocalTextStyle.current
    val typography = MaterialTheme.typography
    val notationTextStyle = remember(localTextStyle, typography) { localTextStyle.merge(typography.bodyLarge).copy(color = Color.Unspecified) }
    val fontResolver = LocalFontFamilyResolver.current
    val notationPainting = remember(density, fontResolver, notationTextStyle) {
        NotationPainting(density, fontResolver, notationTextStyle)
    }
    // A unit is the lyrics' text size over the renderer's, so the staff and its words grow with the lyrics: by the reader's
    // text size and by the system's font size alike (which Android scales nonlinearly, hence the text size converted).
    val notationUnit = with(density) { notationTextStyle.fontSize.toDp() } * fontScale / AbcSystem.TEXT_SIZE
    val partChoices = remember(hasPartChoices) { if (hasPartChoices) NotationPartChoices() else null }
    return rememberSongNotations(
        blocks = model.song.blocks,
        availableWidth = with(density) { if (availableWidth.isSpecified) availableWidth.roundToPx() else 0 },
        narrowestWidth = notationPageWidth(
            availableWidth = availableWidth,
            columnWidth = NARROW_COLUMN_WIDTH * fontScale,
            cardPadding = CARD_PADDING,
            notationUnit = notationUnit,
        ),
        widestWidth = notationPageWidth(
            availableWidth = availableWidth,
            columnWidth = MAX_COLUMN_WIDTH * fontScale,
            cardPadding = CARD_PADDING,
            notationUnit = notationUnit,
        ),
        unitSize = with(density) { notationUnit.toPx() },
        chords = model.notationChords,
        songTitle = model.song.metadata.title,
        painting = notationPainting,
        partChoices = partChoices,
    )
}
