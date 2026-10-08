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

import com.pandulapeter.campfire.chordpro.model.GridToken

/** The words of a line, the cells of a grid line and the staff of a tab, see [words]. */
internal object ChordProTokens {

    private val barLines = setOf("|", "||", "|.", "|:", ":|", ":|:")
    private val voltaRegex = Regex(":?\\|\\d+>?")

    const val GRID_CELL_CHORD_SEPARATOR = "~"

    private const val STAFF_DASH = '-'
    private const val MINIMUM_STAFF_DASH_COUNT = 3

    /** One word of a line and the range it covers, see [words]. */
    data class Word(val range: IntRange, val value: String)

    /**
     * The words of [line]: every run of characters no whitespace separates, each with the range it sits in, so that
     * a caller rewriting one of them can put the answer back in the same columns.
     *
     * Scanned by hand rather than with a `\\S+` regex. `\\s` is the six ASCII spaces to the JVM's and to
     * Kotlin/Native's regex engines and every Unicode space to a browser's, so the same line would be read one way
     * on a phone and another on the web; and a chart pasted from a web page is full of non-breaking spaces, which
     * the ASCII answer glues onto the chord beside them. [Char.isWhitespace] is the same answer on every platform,
     * and the one `trim` and [isStaffLine] already give this module.
     */
    fun words(line: String): List<Word> {
        val words = mutableListOf<Word>()
        var index = 0
        while (index < line.length) {
            while (index < line.length && line[index].isWhitespace()) index++
            if (index == line.length) break
            val start = index
            while (index < line.length && !line[index].isWhitespace()) index++
            words += Word(range = start until index, value = line.substring(start, index))
        }
        return words
    }

    /**
     * Splits a grid line into tokens. ChordPro puts whatever comes before the first bar line in the left margin and
     * whatever follows the last one in the right margin, so on a line that has a bar both are text: a margin label
     * such as `A` or `Coda` names a part of the song, and taking it for a chord would transpose it. A `/` marks where a
     * chord is played and is not one either.
     */
    fun parseGridTokens(trimmedLine: String): List<GridToken> {
        val words = words(trimmedLine).map { it.value }
        val firstBarIndex = words.indexOfFirst { isBar(it) }
        val lastBarIndex = words.indexOfLast { isBar(it) }
        return words.mapIndexed { index, word ->
            when {
                firstBarIndex >= 0 && (index < firstBarIndex || index > lastBarIndex) -> GridToken.Text(word)
                isBar(word) -> GridToken.Bar(word)
                word == "." -> GridToken.Beat
                word == "%" || word == "%%" -> GridToken.Repeat(word)
                word == "/" -> GridToken.Text(word)
                else -> GridToken.Chord(word)
            }
        }
    }

    /** The bar lines of a grid, the repeats and the voltas (`|1`, `:|2`, `:|2>`) included. */
    fun isBar(word: String) = word in barLines || voltaRegex.matches(word)

    /**
     * The chords of one grid cell: ChordPro writes several chords into a cell by joining them with a `~`, and each
     * of them is a chord of its own to transpose or respell.
     */
    fun cellChords(cell: String) = cell.split(GRID_CELL_CHORD_SEPARATOR)

    /**
     * A tablature line: enough dashes to be a staff, and made mostly of the characters a staff is made of. The letters
     * of a technique (`h`, `p`, `x`, …) and the string name in front of the line are the minority that is allowed. It
     * is what tells the staff of a `{start_of_tab}` environment from the chord names above it and the notes around
     * it, for the transposer (which moves frets on the one and chord names on the other) and for the viewer (which
     * cuts a staff at its columns and never a line of prose).
     */
    fun isStaffLine(line: String): Boolean {
        var dashCount = 0
        var staffCharacterCount = 0
        var otherCharacterCount = 0
        line.forEach { character ->
            when {
                character == STAFF_DASH -> {
                    dashCount++
                    staffCharacterCount++
                }

                character == '|' || character.isDigit() -> staffCharacterCount++
                character.isWhitespace() -> Unit
                else -> otherCharacterCount++
            }
        }
        return dashCount >= MINIMUM_STAFF_DASH_COUNT && staffCharacterCount >= otherCharacterCount
    }
}
