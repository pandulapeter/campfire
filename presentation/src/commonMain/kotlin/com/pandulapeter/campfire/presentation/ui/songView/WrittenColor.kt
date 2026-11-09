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
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.presentation.ui.components.legibleOn

/** A colour a song's file asks for (`0xRRGGBB`), moved only as far as reading on [background] needs ([legibleOn]). */
internal fun writtenColorOn(background: Color, color: Int) = legibleOn(background, Color(OPAQUE or color))

/** The colour of a line's words: the `{textcolour}` it was written under, made to read on the surface, else the theme's. */
@Composable
internal fun lyricsColor(line: ChordProLine.Lyrics): Color {
    val background = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    return remember(line.color, background, onSurface) { line.color?.let { writtenColorOn(background, it) } ?: onSurface }
}

private const val OPAQUE = 0xFF000000.toInt()
