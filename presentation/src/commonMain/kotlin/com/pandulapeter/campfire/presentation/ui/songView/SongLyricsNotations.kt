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
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.ui.notation.NotationPainting
import com.pandulapeter.campfire.presentation.ui.notation.NotationPartChoices
import com.pandulapeter.campfire.presentation.ui.notation.SongNotations
import com.pandulapeter.campfire.presentation.ui.notation.notationPageWidth
import com.pandulapeter.campfire.presentation.ui.notation.rememberSongNotations
import com.pandulapeter.campfire.presentation.ui.songView.model.SongLyricsModel

/**
 * The staff notation of the song's ABC tunes, which replaces their text once it has been rendered: for a page as wide as
 * the widest column the song can have here ([maxColumnWidth]), each SVG unit as large as the text size makes it. Its
 * words are laid out in the lyrics' typeface at the sizes abc2svg asks for, so the style they start from is not scaled.
 *
 * @param hasPartChoices Whether a score is engraved one part at a time; not in the editor's preview, which is there to
 * show what is being written.
 */
@Composable
internal fun rememberSongLyricsNotations(
    model: SongLyricsModel,
    availableWidth: Dp,
    maxColumnWidth: Dp,
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
    val notationUnit = NOTATION_UNIT * fontScale
    val partChoices = remember(hasPartChoices) { if (hasPartChoices) NotationPartChoices() else null }
    return rememberSongNotations(
        blocks = model.song.blocks,
        width = notationPageWidth(
            availableWidth = availableWidth,
            maxColumnWidth = maxColumnWidth,
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

/** What one SVG unit of engraved notation is drawn as at the default text size, where the column is wide enough. */
private val NOTATION_UNIT = 1.dp
