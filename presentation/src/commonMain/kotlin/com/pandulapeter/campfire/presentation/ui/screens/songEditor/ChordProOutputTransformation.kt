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
 * and they are the viewer's, so that a chord looks like a chord in both places. The tokens come through a
 * [ChordProTokenCache], so a value that only moved the caret costs the styles and a content comparison. Compose
 * discards the transformed buffer after each display, so its current API still requires applying every style again.
 */
internal class ChordProOutputTransformation(
    private val tokenCache: ChordProTokenCache,
    private val directiveName: SpanStyle,
    private val directiveValue: SpanStyle,
    private val chord: SpanStyle,
    private val annotation: SpanStyle,
    private val comment: SpanStyle,
) : OutputTransformation {

    override fun TextFieldBuffer.transformOutput() {
        tokenCache.tokensOf(originalText).forEach { token ->
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
            tokenCache: ChordProTokenCache,
            primaryColor: Color,
            secondaryColor: Color,
            outlineColor: Color,
        ) = ChordProOutputTransformation(
            tokenCache = tokenCache,
            directiveName = SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold),
            directiveValue = SpanStyle(color = secondaryColor),
            chord = SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold),
            annotation = SpanStyle(fontStyle = FontStyle.Italic),
            comment = SpanStyle(color = outlineColor, fontStyle = FontStyle.Italic),
        )
    }
}

/**
 * The tokens of the text the editor holds, kept from one run of [ChordProOutputTransformation] to the next. The
 * field runs its output transformation for every new value of its state, and most of those differ from the one
 * before only in where the caret is: a tap, an arrow key, every pointer event of a selection being dragged. The
 * tokens depend on the text alone, so they are only worked out again once that has changed.
 *
 * It is a holder of its own rather than a field of the transformation, because the transformation carries the
 * colours and is made anew on every frame of a theme change, while this has to outlive all of them.
 */
internal class ChordProTokenCache {

    private var text: String? = null
    private var tokens = emptyList<ChordProHighlighter.Token>()

    /** A selection change may supply a different [CharSequence] with the same content. */
    fun tokensOf(text: CharSequence): List<ChordProHighlighter.Token> {
        if (!text.matches(this.text)) {
            val changedText = text.toString()
            tokens = ChordProHighlighter.tokenize(changedText)
            this.text = changedText
        }
        return tokens
    }

    private fun CharSequence.matches(cached: String?): Boolean {
        if (cached == null || length != cached.length) return false
        for (index in indices) {
            if (this[index] != cached[index]) return false
        }
        return true
    }
}
