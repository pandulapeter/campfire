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

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.pandulapeter.campfire.chordpro.model.ChordProLine

/** The text of the line, set in the bold, italic, and underlined ranges its markup gave it. */
internal fun ChordProLine.Lyrics.formattedText() = formattedText(text, formatting)

/** [text] set in the bold, italic, and underlined ranges of [formatting]. */
internal fun formattedText(text: String, formatting: List<ChordProLine.Lyrics.Formatting>) = AnnotatedString(
    text = text,
    spanStyles = formatting.map { range ->
        AnnotatedString.Range(
            item = when (range.style) {
                ChordProLine.Lyrics.Formatting.Style.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                ChordProLine.Lyrics.Formatting.Style.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                ChordProLine.Lyrics.Formatting.Style.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
            },
            start = range.start.coerceIn(0, text.length),
            end = range.end.coerceIn(0, text.length),
        )
    },
)
