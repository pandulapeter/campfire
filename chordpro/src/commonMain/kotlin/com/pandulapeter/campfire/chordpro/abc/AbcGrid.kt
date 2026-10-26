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

import com.pandulapeter.campfire.chordpro.abc.AbcBars.Token

/**
 * Writes the lines of a `{start_of_grid}` from [AbcBars]' tokens: one grid line per line of music, one grid bar per
 * ABC bar, its chords as its cells, in order rather than on beats, since placing them needs note lengths the tokenizer
 * doesn't read. A bar with music and no chord is `%`, the chord before it going on, or `.` before the tune's first
 * chord, when there is none to go on. A chordless pickup before the tune's first bar line is left out.
 */
internal class AbcGrid private constructor() {

    private val lines = mutableListOf<String>()
    private var words = mutableListOf<String>()
    private var lineHasCell = false
    private val cells = mutableListOf<String>()
    private var hasMusic = false
    private var pendingBar: String? = null
    private var hasSeenBar = false
    private var hasSeenChord = false
    private var breaksAtNextBar = false

    private fun read(tokenLines: List<List<Token>>): List<String> {
        tokenLines.forEach { tokens ->
            tokens.forEach(::read)
            endLine()
        }
        closeBar()
        flushPendingBar()
        if (lineHasCell) {
            if (words.lastOrNull()?.let(::isGridBar) != true) words += PLAIN_BAR
            lines += words.joinToString(" ")
        } else if (words.isNotEmpty() && lines.isNotEmpty() && lines.last().endsWith(" $PLAIN_BAR")) {
            // A closing bar line written alone on the last line (`|]`) replaces the plain one the line before ends in.
            lines[lines.lastIndex] = lines.last().dropLast(PLAIN_BAR.length) + words.last()
        }
        return lines
    }

    private fun read(token: Token) {
        when (token) {
            is Token.Chord -> {
                flushPendingBar()
                cells += token.name
                hasMusic = true
                hasSeenChord = true
            }
            Token.Note -> {
                flushPendingBar()
                hasMusic = true
            }
            is Token.Bar -> {
                closeBar()
                hasSeenBar = true
                // Two bar lines with no music between them (around a skipped annotation, or `:| |:`) are one, or the
                // grid would show an empty bar; a plain one never replaces a repeat or an ending.
                val previous = pendingBar
                pendingBar = when {
                    previous == null -> token.text
                    previous.startsWith(':') && token.text.endsWith(':') -> "::"
                    token.text == PLAIN_BAR -> previous
                    else -> token.text
                }
                if (breaksAtNextBar) {
                    breaksAtNextBar = false
                    flushPendingBar()
                    emitLine()
                }
            }
            is Token.Volta -> {
                closeBar()
                words += gridBar(pendingBar ?: PLAIN_BAR, token.number)
                pendingBar = null
                hasSeenBar = true
            }
        }
    }

    /** A line of music that ends mid-bar goes on into the next one, and the grid line breaks after that bar instead. */
    private fun endLine() {
        flushPendingBar()
        if (hasMusic) breaksAtNextBar = true else emitLine()
    }

    /**
     * Ends a grid line, unless it has no cell yet: bar lines written alone on a line of music (`|:`) open the next one.
     */
    private fun emitLine() {
        if (!lineHasCell) return
        lines += words.joinToString(" ")
        words = mutableListOf()
        lineHasCell = false
    }

    private fun closeBar() {
        val isPickup = !hasSeenBar && cells.isEmpty()
        val written = when {
            cells.isNotEmpty() -> cells.toList()
            hasMusic && !isPickup -> listOf(if (hasSeenChord) REPEAT else BEAT)
            else -> emptyList()
        }
        if (written.isNotEmpty()) {
            if (words.isEmpty()) words += PLAIN_BAR
            words += written
            lineHasCell = true
        }
        cells.clear()
        hasMusic = false
    }

    private fun flushPendingBar() {
        pendingBar?.let { words += gridBar(it, volta = null) }
        pendingBar = null
    }

    companion object {

        private const val PLAIN_BAR = "|"
        private const val REPEAT = "%"
        private const val BEAT = "."

        /** The grid lines of [tokenLines], or none for a tune with no chord symbol, which gets no grid. */
        fun lines(tokenLines: List<List<Token>>): List<String> =
            if (tokenLines.none { tokens -> tokens.any { it is Token.Chord } }) emptyList() else AbcGrid().read(tokenLines)

        /**
         * [abc] in the bar lines `ChordProTokens.isBar` reads: a repeat sign keeps its sides, `|]` ends the piece as
         * `|.`, a thick double bar (`[|`, `||`) is `||`, and a volta takes the place of the bar line before it, which
         * ChordPro writes as one token (`|1`, `:|2`).
         */
        private fun gridBar(abc: String, volta: String?): String {
            val endsRepeat = abc.startsWith(':')
            val startsRepeat = abc.endsWith(':')
            return when {
                volta != null -> (if (endsRepeat) ":|" else PLAIN_BAR) + volta.ifEmpty { "1" }
                endsRepeat && startsRepeat -> ":|:"
                endsRepeat -> ":|"
                startsRepeat -> "|:"
                abc == "[|]" -> PLAIN_BAR
                abc.endsWith("|]") -> "|."
                abc.count { it == '|' } >= 2 || abc.startsWith("[|") -> "||"
                else -> PLAIN_BAR
            }
        }

        private fun isGridBar(word: String) = word.startsWith(PLAIN_BAR) || word.startsWith(":|")
    }
}
