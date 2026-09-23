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
        /** `{title` and the colon after it, plus the `}` that closes the directive: everything that is not the value. */
        DIRECTIVE_NAME,

        /** What the directive is set to, without the closing brace. */
        DIRECTIVE_VALUE,
        CHORD,

        /** `[*text]`, spaces inside the brackets allowed, which the viewer shows in the lyrics rather than as a chord. */
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
        // Chords are not chords on the staff of a tab or inside an environment handed to another program: the brackets
        // there are part of the tablature or of the notation, and the viewer leaves them alone too. The other lines of
        // a tab are rows of chord names, whose brackets the transposition renames.
        var isInTab = false
        var isInDelegate = false
        // The lines and their offsets come from ChordProSyntax rather than from a walk of their own, so that a file
        // written with any of the three line endings is highlighted the way it is parsed.
        val lines = ChordProSyntax.splitLines(text)
        val lineStarts = ChordProSyntax.lineStartOffsets(text)
        lines.forEachIndexed { index, line ->
            val lineStart = lineStarts[index]
            val trimmed = line.trim()
            val directive = if (isInDelegate) ChordProSyntax.matchDelegatedDirective(trimmed) else ChordProSyntax.matchDirective(trimmed)
            when {
                trimmed.startsWith(SOURCE_COMMENT) && !isInDelegate -> tokens += Token(TokenType.COMMENT, lineStart, lineStart + line.length)

                directive != null -> {
                    ChordProSyntax.startOfEnvironment(directive.name)?.let {
                        isInTab = it == TAB_ENVIRONMENT
                        isInDelegate = it in ChordProSyntax.delegateEnvironments
                    }
                    ChordProSyntax.endOfEnvironment(directive.name)?.let {
                        if (it == TAB_ENVIRONMENT) isInTab = false
                        if (it in ChordProSyntax.delegateEnvironments) isInDelegate = false
                    }
                    tokens += directive.tokens(
                        line = line,
                        lineStart = lineStart,
                        valueStart = ChordProSyntax.directiveValueStart(trimmed),
                    )
                }

                // A staff line's brackets are part of the tablature, which the transposition moves by its frets; the
                // brackets of any other line of a tab are chords to it, and are coloured as chords here.
                !isInDelegate && !(isInTab && ChordProSyntax.isStaffLine(line)) -> ChordProSyntax.brackets(line).forEach { bracket ->
                    bracket.token(lineStart)?.let { tokens += it }
                }
            }
        }
        return tokens
    }

    /**
     * The name half runs from the opening brace to the colon, or to the whitespace after the name where the directive
     * has no colon (or to the closing brace when it has no value), and the value half is whatever is left before the closing brace. The two braces are a pair and are
     * coloured as one: a directive only matches when both of them are there, so the closing one is as much a sign of
     * what the line is as the opening one, and leaving it plain made a directive look unfinished after its value.
     */
    private fun ChordProSyntax.Directive.tokens(line: String, lineStart: Int, valueStart: Int?): List<Token> {
        val open = line.indexOf('{')
        val close = line.lastIndexOf('}')
        if (open == -1 || close <= open) return emptyList()
        val hasValue = valueStart != null && !value.isNullOrEmpty()
        // Without a value the whole thing is one token, closing brace included; with one the value splits the name
        // in two and the closing brace becomes a token of its own. The trimmed line the value start was counted on
        // begins at the opening brace.
        val nameEnd = if (hasValue) open + valueStart else close + 1
        val name = Token(TokenType.DIRECTIVE_NAME, lineStart + open, lineStart + nameEnd)
        return if (hasValue) {
            buildList {
                add(name)
                addAll(valueTokens(line.substring(nameEnd, close), lineStart + nameEnd))
                add(Token(TokenType.DIRECTIVE_NAME, lineStart + close, lineStart + close + 1))
            }
        } else {
            listOf(name)
        }
    }

    /**
     * The chords of the text of a comment or a label, offsets into [text]: the brackets the transposition moves there.
     * Only a whole chord name counts (a lowercase minor included), since such a text is drawn as it is written: a
     * `[Chorus x2]` is not moved and a `[*softly]` is not lifted out of it the way an annotation is lifted out of the
     * lyrics.
     */
    fun chordsOfShownText(text: String): List<Token> = ChordProSyntax.brackets(text)
        .filter { it.content.trim().isMovedChordName() }
        .mapNotNull { it.token(0) }

    /**
     * The value of a directive, cut around the chords in it where it is text the song shows (see
     * [ChordProSyntax.hasChordsInValue]): those brackets are moved by the transposition, and the editor says so.
     */
    private fun ChordProSyntax.Directive.valueTokens(value: String, valueStart: Int): List<Token> {
        val chords = if (ChordProSyntax.hasChordsInValue(name)) {
            chordsOfShownText(value).map { it.copy(start = valueStart + it.start, end = valueStart + it.end) }
        } else {
            emptyList()
        }
        val tokens = mutableListOf<Token>()
        var consumedUntil = valueStart
        chords.forEach { chord ->
            if (chord.start > consumedUntil) tokens += Token(TokenType.DIRECTIVE_VALUE, consumedUntil, chord.start)
            tokens += chord
            consumedUntil = chord.end
        }
        if (valueStart + value.length > consumedUntil) tokens += Token(TokenType.DIRECTIVE_VALUE, consumedUntil, valueStart + value.length)
        return tokens
    }

    /** Whether the transposition moves this bracket's content, a lowercase minor (`a` for `Am`) included. */
    private fun String.isMovedChordName() =
        ChordProChordNames.isChordName(this) || ChordProChordNames.lowercaseMinorExpanded(this)?.let(ChordProChordNames::isChordName) == true

    /**
     * The token of a bracket that starts [offset] characters into the text, trimmed and with an empty one left out,
     * because that is how the parser and the transposition read a bracket: a `[ *softly]` is the annotation the viewer
     * will draw in the lyrics, and a `[]` is not a chord to anything downstream. What counts as a chord is decided in
     * one place or in none.
     */
    private fun ChordProSyntax.Bracket.token(offset: Int): Token? {
        val content = content.trim()
        if (content.isEmpty()) return null
        return Token(
            type = if (content.startsWith(ANNOTATION_PREFIX)) TokenType.ANNOTATION else TokenType.CHORD,
            start = offset + range.first,
            end = offset + range.last + 1,
        )
    }

    private const val SOURCE_COMMENT = "#"
    private const val TAB_ENVIRONMENT = "tab"
    private const val ANNOTATION_PREFIX = "*"
}
