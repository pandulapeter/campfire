/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor

import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.pandulapeter.campfire.chordpro.ChordProHighlighter

/**
 * Colours the text as it is typed. Only styles are added, no characters are inserted or removed, so the offsets the
 * text field works with are the offsets of the text itself and no offset mapping is needed.
 *
 * Which parts count as what comes from [ChordProHighlighter], next to the parser; only the styles are decided here,
 * and they are the viewer's, so that a chord looks like a chord in both places.
 */
internal class ChordProOutputTransformation(
    private val directiveName: SpanStyle,
    private val directiveValue: SpanStyle,
    private val chord: SpanStyle,
    private val annotation: SpanStyle,
    private val comment: SpanStyle,
) : OutputTransformation {

    override fun TextFieldBuffer.transformOutput() {
        ChordProHighlighter.tokenize(originalText.toString()).forEach { token ->
            addStyle(
                when (token.type) {
                    ChordProHighlighter.TokenType.DIRECTIVE_NAME -> directiveName
                    ChordProHighlighter.TokenType.DIRECTIVE_VALUE -> directiveValue
                    ChordProHighlighter.TokenType.CHORD -> chord
                    ChordProHighlighter.TokenType.ANNOTATION -> annotation
                    ChordProHighlighter.TokenType.COMMENT -> comment
                },
                token.start,
                token.end,
            )
        }
    }

    companion object {

        /** The same colours the viewer uses, so that the editor and the preview next to it agree. */
        fun of(
            primaryColor: Color,
            secondaryColor: Color,
            outlineColor: Color,
        ) = ChordProOutputTransformation(
            directiveName = SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold),
            directiveValue = SpanStyle(color = secondaryColor),
            chord = SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold),
            annotation = SpanStyle(fontStyle = FontStyle.Italic),
            comment = SpanStyle(color = outlineColor, fontStyle = FontStyle.Italic),
        )
    }
}
