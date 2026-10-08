/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.edit

import com.pandulapeter.campfire.chordpro.ChordNotation
import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.chords.ChordProChordNames
import com.pandulapeter.campfire.chordpro.chords.ChordProDefinitions
import com.pandulapeter.campfire.chordpro.chords.ChordProNashville
import com.pandulapeter.campfire.chordpro.model.GridToken
import com.pandulapeter.campfire.chordpro.syntax.ChordProDirectives
import com.pandulapeter.campfire.chordpro.syntax.ChordProHeaderLayout
import com.pandulapeter.campfire.chordpro.syntax.ChordProLineScanner
import com.pandulapeter.campfire.chordpro.syntax.ChordProLines
import com.pandulapeter.campfire.chordpro.syntax.ChordProMetaItems
import com.pandulapeter.campfire.chordpro.syntax.ChordProTokens
import com.pandulapeter.campfire.chordpro.syntax.ChordProVocabulary.ANNOTATION_MARKER
import com.pandulapeter.campfire.chordpro.syntax.ChordProVocabulary.GRID
import com.pandulapeter.campfire.chordpro.syntax.ChordProVocabulary.KEY
import com.pandulapeter.campfire.chordpro.syntax.ChordProVocabulary.META
import com.pandulapeter.campfire.chordpro.syntax.ChordProVocabulary.TAB
import com.pandulapeter.campfire.chordpro.syntax.ChordProVocabulary.TRANSPOSE
import com.pandulapeter.campfire.chordpro.syntax.MetadataKind

/**
 * Finds the parts of a ChordPro document an editor wants to colour. It lives next to the parser rather than in the
 * UI so that the two cannot drift apart: what counts as a directive or a chord is decided in exactly one place, and
 * only what those look like on screen is left to the caller.
 *
 * Unlike [ChordProParser] this never rejects anything - a document being typed is malformed most of the time, and
 * the half written line under the caret still has to look like what it is becoming.
 */
public object ChordProHighlighter {

    public enum class TokenType {
        /** `{title` and the colon after it, plus the `}` that closes the directive: everything that is not the value. */
        DIRECTIVE_NAME,

        /** What the directive is set to, without the closing brace. */
        DIRECTIVE_VALUE,

        /** A bracketed chord over the lyrics, or a chord cell of a grid, which is written without brackets. */
        CHORD,

        /** `[*text]`, spaces inside the brackets allowed, which the viewer shows in the lyrics rather than as a chord. */
        ANNOTATION,

        /** A whole `#` line, which never reaches the rendered song. */
        COMMENT,

        /**
         * A whole directive line whose value the app reads as something - a time signature, a tempo, a capo, a
         * duration, a transposition, a cover, a link, a language or a chord's shape - but cannot make sense of, so that it is as good as
         * missing from the song. A key is never one: whatever it says is kept and shown as written. It takes the place of the directive's own tokens rather than lying over them.
         */
        INVALID,

        /**
         * A whole directive line that says again what the song can only say once — a second title, a second year, a
         * second key — counted by the one name the directive is known by (see [ChordProHeaderLayout.metadataKind]), so a
         * `{t}` after a `{title}` is one too. The parser takes the first line of the kind that says anything and reads
         * past the rest, so every line after that one is marked, an empty one included; an empty line or an [INVALID]
         * one before it says nothing and leaves the kind free. A `{key}` in the body is also one wherever the header
         * has a line of it, empty or not, since the song starts in the header's. The
         * [repeatable][ChordProHeader.repeatableMetadata] and the [changeable][ChordProHeader.changeableMetadata] kinds
         * are never one. It takes the place of the directive's own tokens.
         */
        DUPLICATE,
    }

    /** [start] is inclusive and [end] exclusive, both offsets into the whole text. */
    public data class Token(
        val type: TokenType,
        val start: Int,
        val end: Int,
    )

    public fun tokenize(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        // The kinds a line has said something for, and whether the header has a key line, which the parser takes the
        // song's key from even where it is empty.
        val saidOnce = mutableSetOf<String>()
        var hasHeaderKey = false
        // The lines and their offsets come from ChordProLines rather than from a walk of their own, so that a file
        // written with any of the three line endings is highlighted the way it is parsed.
        val lines = ChordProLines.splitLines(text)
        val lineStarts = ChordProLines.lineStartOffsets(text)
        val bodyStart = ChordProHeaderLayout.bodyStartIndex(lines)
        ChordProLineScanner.scan(lines).forEach { scanned ->
            val index = scanned.index
            val line = scanned.raw
            val lineStart = lineStarts[index]
            val directive = scanned.directive
            when {
                scanned.isSourceComment -> tokens += Token(TokenType.COMMENT, lineStart, lineStart + line.length)

                directive != null -> {
                    // A directive that opens or closes an environment is read by the environment it leaves open, which
                    // is how a `{start_of_abc: …}` line is left alone like the notation after it.
                    val isInDelegate = scanned.isDelegatedAfter
                    val isUnreadable = !isInDelegate && directive.isUnreadable()
                    val onceOnlyKind = if (isInDelegate || isUnreadable) null else directive.onceOnlyKind()
                    val isReadPast = onceOnlyKind != null &&
                        (onceOnlyKind in saidOnce || (onceOnlyKind == KEY && index >= bodyStart && hasHeaderKey))
                    if (onceOnlyKind == KEY && index < bodyStart) hasHeaderKey = true
                    if (onceOnlyKind != null && directive.valueText().isNotEmpty()) saidOnce += onceOnlyKind
                    tokens += when {
                        isUnreadable -> listOf(Token(TokenType.INVALID, lineStart, lineStart + line.length))
                        isReadPast -> listOf(Token(TokenType.DUPLICATE, lineStart, lineStart + line.length))
                        else -> directive.tokens(
                            line = line,
                            lineStart = lineStart,
                            valueStart = ChordProDirectives.directiveValueStart(scanned.trimmed),
                        )
                    }
                }

                // Chords are not chords on the staff of a tab or inside an environment handed to another program: the
                // brackets there are part of the tablature or of the notation, and the viewer leaves them alone too. The
                // other lines of a tab are rows of chord names, whose brackets the transposition renames. A grid has no
                // brackets at all: its cells are chords as they are, and the transposition renames them.
                scanned.environment == GRID -> tokens += gridChordTokens(line, lineStart)

                !scanned.isDelegated && !(scanned.environment == TAB && ChordProTokens.isStaffLine(line)) -> ChordProDirectives.brackets(line).forEach { bracket ->
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
    private fun ChordProDirectives.Directive.tokens(line: String, lineStart: Int, valueStart: Int?): List<Token> {
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
     * Whether this is a directive the parser reads a value out of, holding a value it then drops: the same functions
     * read it here as there, so that a line is marked exactly when the song comes out without what it says. A
     * `{meta: time 3/4}` is the `{time}` it stands for. A directive with no value is left alone: it is what the
     * editor writes into the header for the value to be typed into, and a `{transpose}` without one is a valid one
     * besides, going back to the transposition before it.
     */
    private fun ChordProDirectives.Directive.isUnreadable(): Boolean {
        val directive = ChordProMetaItems.standardMeta(this) ?: this
        val value = valueText()
        if (value.isEmpty()) return false
        ChordProDefinitions.selectorOf(directive.name)?.let { selector ->
            return ChordProDefinitions.read(value, selector.takeIf { it.isNotEmpty() }) == ChordProDefinitions.Reading.Invalid
        }
        return when {
            !ChordProMetaItems.isReadableValue(directive.name, value) -> true
            directive.name == TRANSPOSE -> ChordProParser.transposeSemitones(value) == null
            ChordProMetaItems.isCoverMeta(directive) -> ChordProMetaItems.cover(directive) == null
            ChordProMetaItems.isLinkMeta(directive) -> ChordProMetaItems.link(directive) == null
            (ChordProHeaderLayout.metadataAliases[directive.name] ?: directive.name) == ChordProMetaItems.LANGUAGE_NAME ||
                ChordProMetaItems.isLanguageMeta(directive) -> ChordProMetaItems.language(directive) == null
            else -> false
        }
    }

    /** What the directive is set to, trimmed, a `{meta: …}` item's without its name. */
    private fun ChordProDirectives.Directive.valueText(): String {
        val directive = ChordProMetaItems.standardMeta(this) ?: this
        return (if (directive.name == META) ChordProMetaItems.metaValue(directive) else directive.value)?.trim().orEmpty()
    }

    /** The kind of metadata this directive declares where a song can only be one of it, see [TokenType.DUPLICATE]. */
    private fun ChordProDirectives.Directive.onceOnlyKind() = ChordProHeaderLayout.metadataKind(this)
        ?.takeIf { kind -> MetadataKind.of(kind)?.let { it.isRepeatable || it.isTimingChange } != true }

    /**
     * The chord cells of a grid line, read the way the parser reads them, so that a margin label or a `/` is left
     * plain. The words are counted on the line as it is written rather than trimmed, which is the same words at the
     * offsets the editor needs.
     */
    private fun gridChordTokens(line: String, lineStart: Int): List<Token> = ChordProTokens.words(line)
        .zip(ChordProTokens.parseGridTokens(line.trim()))
        .filter { (_, token) -> token is GridToken.Chord }
        .map { (word, _) -> Token(TokenType.CHORD, lineStart + word.range.first, lineStart + word.range.last + 1) }

    /**
     * The chords of the text of a comment or a label, offsets into [text]: the brackets the transposition moves there.
     * Only a whole chord name counts (a lowercase minor included), since such a text is drawn as it is written: a
     * `[Chorus x2]` is not moved and a `[*softly]` is not lifted out of it the way an annotation is lifted out of the
     * lyrics. [notation] is the one the text is shown in, which in a numbering makes a step of the key a chord too.
     */
    public fun chordsOfShownText(text: String, notation: ChordNotation = ChordNotation.STANDARD): List<Token> = ChordProDirectives.brackets(text)
        .filter { bracket -> bracket.content.trim().let { it.isMovedChordName() || (notation.isNumbering && ChordProNashville.isDegree(it)) } }
        .mapNotNull { it.token(0) }

    /**
     * The value of a directive, cut around the chords in it where it is text the song shows (see
     * [ChordProDirectives.hasChordsInValue]): those brackets are moved by the transposition, and the editor says so.
     */
    private fun ChordProDirectives.Directive.valueTokens(value: String, valueStart: Int): List<Token> {
        val chords = when {
            ChordProDirectives.hasChordsInValue(name) -> chordsOfShownText(value).map { it.copy(start = valueStart + it.start, end = valueStart + it.end) }
            // The chord a definition names is the first word of its value, written without brackets.
            ChordProDefinitions.selectorOf(name) != null -> {
                val start = value.indexOfFirst { !it.isWhitespace() }
                val end = if (start < 0) -1 else (start until value.length).firstOrNull { value[it].isWhitespace() } ?: value.length
                if (start >= 0 && value.substring(start, end).isMovedChordName()) listOf(Token(TokenType.CHORD, valueStart + start, valueStart + end)) else emptyList()
            }
            else -> emptyList()
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

    /** Whether the transposition moves this bracket's content, a lowercase minor (`a` for `Am`) and a Latin name included. */
    private fun String.isMovedChordName() = ChordProChordNames.isChordName(this) ||
        ChordProChordNames.lowercaseMinorExpanded(this) != null ||
        ChordProChordNames.latinExpanded(this) != null

    /**
     * The token of a bracket that starts [offset] characters into the text, trimmed and with an empty one left out,
     * because that is how the parser and the transposition read a bracket: a `[ *softly]` is the annotation the viewer
     * will draw in the lyrics, and a `[]` is not a chord to anything downstream. What counts as a chord is decided in
     * one place or in none.
     */
    private fun ChordProDirectives.Bracket.token(offset: Int): Token? {
        val content = content.trim()
        if (content.isEmpty()) return null
        return Token(
            type = if (content.startsWith(ANNOTATION_MARKER)) TokenType.ANNOTATION else TokenType.CHORD,
            start = offset + range.first,
            end = offset + range.last + 1,
        )
    }
}
