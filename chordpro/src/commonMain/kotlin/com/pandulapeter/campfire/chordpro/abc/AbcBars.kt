/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.abc

import com.pandulapeter.campfire.chordpro.chords.ChordProChordNames

/**
 * The bar lines, voltas, and chord symbols of a tune body, line by line. It reads only what a chord grid needs; every
 * other character is music it skips, and it stays that small: notation is a renderer's job, never a reason to grow it.
 */
internal object AbcBars {

    sealed interface Token {

        /** A bar line as ABC spells it (`|`, `|]`, `[|`, `::`, …). */
        data class Bar(val text: String) : Token

        /** The first ending a volta (`[1`, `|2`, `:|1,3`) starts, its number only. */
        data class Volta(val number: String) : Token

        data class Chord(val name: String) : Token

        /** A note, rest, or chord of notes: the bar it is in has music, chord symbol or not. */
        data object Note : Token
    }

    private const val BAR_CHARACTERS = "|:"
    private const val ANNOTATION_MARKS = "^_<>@"
    private const val NOTE_LETTERS = "ABCDEFGabcdefgzZx"

    /**
     * The tokens of each line of music in [body] (the lines after the tune header): field lines, comments, and
     * `%%begin…`/`%%end…` blocks are left out, and a line ending in a `\` is joined to the next line of music. Only the
     * first voice is read (`V:`, `[V:…]`): the others are accompaniment written under the same bars, which would
     * otherwise read as rows of bars of their own.
     */
    fun lines(body: List<String>): List<List<Token>> {
        val lines = mutableListOf<List<Token>>()
        var pending = mutableListOf<Token>()
        var block: String? = null
        val voices = Voices()
        for (line in body) {
            val openBlock = block
            if (openBlock != null) {
                if (AbcLines.closesBlock(line, openBlock)) block = null
                continue
            }
            block = AbcLines.blockOpenedBy(line)
            val field = AbcLines.field(line)
            if (field?.letter == 'V') voices.switchTo(field.value)
            if (block != null || line.isBlank() || AbcLines.isComment(line) || field != null) continue
            val music = AbcLines.withoutComment(line).trimEnd()
            val isContinued = music.endsWith('\\')
            tokenize(if (isContinued) music.dropLast(1) else music, pending, voices)
            if (!isContinued) {
                lines += pending
                pending = mutableListOf()
            }
        }
        if (pending.isNotEmpty()) lines += pending
        return lines
    }

    private fun tokenize(line: String, lineTokens: MutableList<Token>, voices: Voices) {
        val skipped = mutableListOf<Token>()
        var index = 0
        while (index < line.length) {
            val character = line[index]
            val tokens = if (voices.isFirst) lineTokens else skipped.apply { clear() }
            index = when {
                character == '"' -> quoted(line, index, tokens)
                character == '!' || character == '+' || character == '{' -> decorationEnd(line, index)
                character == '[' -> bracketed(line, index, tokens, voices)
                character in BAR_CHARACTERS -> barLine(line, index, tokens)
                character in NOTE_LETTERS -> (index + 1).also { tokens += Token.Note }
                else -> index + 1
            }
        }
    }

    /** A chord symbol, or an annotation, an alternative chord (`"(Em)"`), or any other text, which add nothing. */
    private fun quoted(line: String, start: Int, tokens: MutableList<Token>): Int {
        val end = line.indexOf('"', start + 1).takeIf { it >= 0 } ?: line.length
        val symbol = line.substring(start + 1, end).trim()
        val isChord = symbol.isNotEmpty() && symbol[0] !in ANNOTATION_MARKS && symbol[0] != '(' && ChordProChordNames.isChordName(symbol)
        if (isChord) tokens += Token.Chord(symbol)
        return end + 1
    }

    /**
     * Past a decoration (`!trill!`, `+fermata+`) or a group of grace notes (`{g}`). One that reaches across a space or
     * a bar line is no such thing: older tunes mark line breaks with a lone `!`, and skipping to the next one would
     * swallow the bars between them. The walk stops at the first such character, or at a second `{` (grace notes do not
     * nest), rather than at the closing mark, so a line of unclosed `{` stays linear.
     */
    private fun decorationEnd(line: String, start: Int): Int {
        val opening = line[start]
        val closing = if (opening == '{') '}' else opening
        var index = start + 1
        while (index < line.length) {
            val character = line[index]
            if (character == closing) return index + 1
            if (character.isWhitespace() || character == '|' || character == '"' || (opening == '{' && character == '{')) break
            index++
        }
        return start + 1
    }

    /** An inline field (`[K:D]`), a volta (`[1`), a `[|` bar line, or a chord of notes (`[CEG]`). */
    private fun bracketed(line: String, start: Int, tokens: MutableList<Token>, voices: Voices): Int {
        val next = line.getOrNull(start + 1)
        return when {
            next == null -> start + 1
            next == '|' -> barLine(line, start, tokens)
            next.isDigit() -> volta(line, start + 1, tokens)
            next.isLetter() && line.getOrNull(start + 2) == ':' -> line.indexOf(']', start).let { end ->
                val fieldEnd = if (end < 0) line.length else end
                if (next == 'V') voices.switchTo(line.substring(start + 3, fieldEnd))
                if (end < 0) line.length else end + 1
            }
            else -> line.indexOf(']', start).let { end ->
                tokens += Token.Note
                if (end < 0) line.length else end + 1
            }
        }
    }

    private fun barLine(line: String, start: Int, tokens: MutableList<Token>): Int {
        var index = if (line[start] == '[') start + 1 else start
        while (index < line.length && line[index] in BAR_CHARACTERS) index++
        if (line.getOrNull(index) == ']' && line[index - 1] == '|') index++
        val text = line.substring(start, index)
        if ('|' !in text && text.length < 2) return index
        tokens += Token.Bar(text)
        return if (line.getOrNull(index)?.isDigit() == true) volta(line, index, tokens) else index
    }

    /** A volta's numbers (`1`, `1,3`, `1-2`), of which the grid keeps the first, its syntax having no list. */
    private fun volta(line: String, start: Int, tokens: MutableList<Token>): Int {
        var index = start
        while (index < line.length && (line[index].isDigit() || line[index] == ',' || line[index] == '-')) index++
        tokens += Token.Volta(line.substring(start, index).takeWhile { it.isDigit() })
        return index
    }

    /** The voice the music is in, and the first one the tune names, the only one read. */
    private class Voices {
        private var first: String? = null
        private var current: String? = null

        /** Music before any voice is named is the first voice's, as the standard has it. */
        val isFirst get() = current == null || current == first

        /** Switches to the voice a `V:` value names by its first word, its clef and name following it. */
        fun switchTo(value: String) {
            val id = value.trim().takeWhile { !it.isWhitespace() }
            if (first == null) first = id
            current = id
        }
    }
}
