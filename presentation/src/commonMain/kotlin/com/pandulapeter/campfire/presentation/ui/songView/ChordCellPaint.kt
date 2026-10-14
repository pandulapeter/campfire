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

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor

/** What the cells of a song's chord rows are drawn with: the theme's colors and font, and a measurer for the diagrams. */
internal class ChordCellPaint(
    val nameColor: Color,
    val secondNameColor: Color,
    val lineColor: Color,
    val mutedColor: Color,
    val backgroundColor: Color,
    val diagramTextMeasurer: TextMeasurer,
    val diagramFontFamily: FontFamily?,
)

@Composable
internal fun chordCellPaint() = ChordCellPaint(
    nameColor = LocalSecondAccentColor.current,
    secondNameColor = MaterialTheme.colorScheme.onSurfaceVariant,
    lineColor = MaterialTheme.colorScheme.onSurface,
    mutedColor = MaterialTheme.colorScheme.onSurfaceVariant,
    backgroundColor = MaterialTheme.colorScheme.surface,
    // The page's measurer keeps nothing, since whoever asks keeps what it measured; a diagram measures its base fret's
    // number on every draw, so it gets one with a cache of its own, as the composed diagram had.
    diagramTextMeasurer = rememberTextMeasurer(),
    diagramFontFamily = MaterialTheme.typography.bodyMedium.fontFamily,
)
