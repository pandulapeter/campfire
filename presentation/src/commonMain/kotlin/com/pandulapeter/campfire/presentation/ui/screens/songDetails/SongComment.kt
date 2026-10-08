/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.chordpro.edit.ChordProHighlighter
import com.pandulapeter.campfire.chordpro.model.CommentStyle
import com.pandulapeter.campfire.presentation.ui.components.scaled
import com.pandulapeter.campfire.presentation.ui.theme.LocalSecondAccentColor

/**
 * A `{comment}` line: a layout section of its own where it stands between two sections, so that it can sit between two
 * columns freely, and a part of the section it was written in otherwise, folded away with it (see [toRenderSections]).
 */
@Composable
internal fun SongComment(
    modifier: Modifier = Modifier,
    comment: RenderSection.Comment,
    notation: ChordNotation,
    fontScale: Float,
) {
    val style = MaterialTheme.typography.bodyMedium.scaled(fontScale).let {
        if (comment.style == CommentStyle.ITALIC) it.copy(fontStyle = FontStyle.Italic) else it
    }
    val chordColor = LocalSecondAccentColor.current
    // The transposition moves the brackets of a comment as it moves those of the lyrics, so they are drawn as chords.
    val annotatedText = remember(comment.text, notation, chordColor) {
        buildAnnotatedString {
            append(comment.text)
            ChordProHighlighter.chordsOfShownText(comment.text, notation).forEach { addStyle(SpanStyle(color = chordColor, fontWeight = FontWeight.Bold), it.start, it.end) }
        }
    }
    val text = @Composable { boxModifier: Modifier ->
        Text(
            modifier = boxModifier,
            text = annotatedText,
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (comment.style == CommentStyle.BOX) {
        text(
            modifier
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = MaterialTheme.shapes.small,
                )
                // The box grows with the text it holds, so the room inside its border grows too, or a large text size
                // would set the words against the line.
                .padding(horizontal = COMMENT_BOX_HORIZONTAL_PADDING * fontScale, vertical = COMMENT_BOX_VERTICAL_PADDING * fontScale)
        )
    } else {
        text(modifier)
    }
}

private val COMMENT_BOX_HORIZONTAL_PADDING = 12.dp
private val COMMENT_BOX_VERTICAL_PADDING = 8.dp
