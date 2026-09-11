/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro

/**
 * Finds the parts of a ChordPro document an editor wants to colour. It lives next to the parser rather than in the
 * UI so that the two cannot drift apart: what counts as a directive or a chord is decided in exactly one place, and
 * only what those look like on screen is left to the caller.
 *
 * Unlike [ChordProParser] this never rejects anything - a document being typed is malformed most of the time, and
 * the half written line under the caret still has to look like what it is becoming.
 */
object ChordProHighlighter {

    enum class TokenType {
        /** `{title` and the colon after it: the part that says what the directive is. */
        DIRECTIVE_NAME,

        /** What the directive is set to, without the closing brace. */
        DIRECTIVE_VALUE,
        CHORD,

        /** `[*text]`, which the viewer shows in the lyrics rather than as a chord. */
        ANNOTATION,

        /** A whole `#` line, which never reaches the rendered song. */
        COMMENT,
    }

    /** [start] is inclusive and [end] exclusive, both offsets into the whole text. */
    data class Token(
        val type: TokenType,
        val start: Int,
        val end: Int,
    )

    fun tokenize(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        // Chords are not chords inside a tab: the brackets there are part of the tablature, and the viewer leaves
        // them alone too.
        var isInsideTab = false
        var lineStart = 0
        while (lineStart <= text.length) {
            val lineBreak = text.indexOf('\n', lineStart)
            val lineEnd = if (lineBreak == -1) text.length else lineBreak
            val line = text.substring(lineStart, lineEnd)
            val trimmed = line.trim()
            val directive = ChordProSyntax.matchDirective(trimmed.removeSuffix("\r"))
            when {
                trimmed.startsWith(SOURCE_COMMENT) -> tokens += Token(TokenType.COMMENT, lineStart, lineEnd)

                directive != null -> {
                    ChordProSyntax.startOfEnvironment(directive.name)?.let { isInsideTab = it == TAB_ENVIRONMENT }
                    ChordProSyntax.endOfEnvironment(directive.name)?.let { if (it == TAB_ENVIRONMENT) isInsideTab = false }
                    tokens += directive.tokens(line = line, lineStart = lineStart)
                }

                !isInsideTab -> ChordProSyntax.chordRegex.findAll(line).forEach { match ->
                    tokens += Token(
                        type = if (match.groupValues[1].startsWith(ANNOTATION_PREFIX)) TokenType.ANNOTATION else TokenType.CHORD,
                        start = lineStart + match.range.first,
                        end = lineStart + match.range.last + 1,
                    )
                }
            }
            if (lineBreak == -1) break
            lineStart = lineBreak + 1
        }
        return tokens
    }

    /**
     * The name half runs from the opening brace to the colon (or to the closing brace when the directive has no
     * value), and the value half is whatever is left before the closing brace.
     */
    private fun ChordProSyntax.Directive.tokens(line: String, lineStart: Int): List<Token> {
        val open = line.indexOf('{')
        val close = line.lastIndexOf('}')
        if (open == -1 || close <= open) return emptyList()
        val colon = line.indexOf(':', startIndex = open)
        val hasValue = colon in (open + 1) until close && !value.isNullOrEmpty()
        // Without a value the whole thing is the name, closing brace included; with one the brace belongs to
        // neither half, so that the value ends where the text does.
        val nameEnd = if (hasValue) colon + 1 else close + 1
        val name = Token(TokenType.DIRECTIVE_NAME, lineStart + open, lineStart + nameEnd)
        return if (hasValue) {
            listOf(name, Token(TokenType.DIRECTIVE_VALUE, lineStart + nameEnd, lineStart + close))
        } else {
            listOf(name)
        }
    }

    private const val SOURCE_COMMENT = "#"
    private const val TAB_ENVIRONMENT = "tab"
    private const val ANNOTATION_PREFIX = "*"
}
