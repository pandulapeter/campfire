/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.language

/**
 * Where the units of one line of text (a character, or a letter with its marks) are drawn and read: the part of the
 * Unicode bidirectional algorithm (UAX #9) a line of lyrics or chords needs, with no explicit embeddings or isolates.
 * Right-to-left letters ([isStrongRtl]) stand at an odd level, Latin letters and digits inside a right-to-left line at
 * level 2, and the spaces and punctuation between them take the direction of what is on both sides, else the line's.
 *
 * Its levels come out the same for a line in reading order and for the same line in the order it is drawn, which is
 * what lets a PDF that draws its glyphs from the left be read back: a number among left-to-right words is set by the
 * strong letters on both of its sides rather than by the one before it, and that one rule is where it differs from the
 * algorithm proper ("abc שלום 123", where the number follows the Hebrew word, is drawn as if it led it).
 */
public object BidiOrder {
    /** The embedding level of each of [units], in [direction]'s line: even is drawn from the left, odd from the right. */
    public fun levels(units: List<CharSequence>, direction: WritingDirection): IntArray {
        val isRtl = direction == WritingDirection.RIGHT_TO_LEFT
        val classes = IntArray(units.size) { classOf(units[it]) }
        // A number among left-to-right words reads with them; only one with right-to-left text on both sides is part of it.
        if (!isRtl) classes.indices.filter { classes[it] == NUMBER }.forEach { index ->
            val before = (index - 1 downTo 0).firstOrNull { classes[it] == LEFT || classes[it] == RIGHT }?.let { classes[it] } ?: LEFT
            val after = (index + 1 until classes.size).firstOrNull { classes[it] == LEFT || classes[it] == RIGHT }?.let { classes[it] } ?: LEFT
            classes[index] = if (before == RIGHT && after == RIGHT) EMBEDDED_NUMBER else LEFT
        }
        val base = if (isRtl) RIGHT else LEFT
        return IntArray(units.size) { index ->
            when (classes[index]) {
                RIGHT -> 1
                LEFT, NUMBER -> if (isRtl) 2 else 0
                EMBEDDED_NUMBER -> 2
                else -> {
                    val before = (index - 1 downTo 0).firstOrNull { classes[it] != NEUTRAL }?.let { classes[it] } ?: base
                    val after = (index + 1 until classes.size).firstOrNull { classes[it] != NEUTRAL }?.let { classes[it] } ?: base
                    if (isRtl) {
                        // Numbers act as right-to-left text on what is between them, so "1 2" stays apart, while "12:30"
                        // and "Psalm 133" are one left-to-right run.
                        val isBetweenNumbers = before == NUMBER && after == NUMBER && units[index].any { it.isWhitespace() }
                        if (before != RIGHT && after != RIGHT && !isBetweenNumbers) 2 else 1
                    } else {
                        if ((before == RIGHT || before == EMBEDDED_NUMBER) && (after == RIGHT || after == EMBEDDED_NUMBER)) 1 else 0
                    }
                }
            }
        }
    }

    /** The units of a line in reading order whose [levels] are given, as the indices of the order they are drawn in. */
    public fun drawnOrder(levels: IntArray): IntArray {
        val order = IntArray(levels.size) { it }
        for (level in (levels.maxOrNull() ?: 0) downTo 1) reverseRuns(order, levels, level)
        return order
    }

    /**
     * The units of a line as they are drawn from the left, whose [levels] are given in that order, as the indices of the
     * order they are read in: [drawnOrder] undone, its reversals made in the opposite order.
     */
    public fun readingOrder(levels: IntArray): IntArray {
        val order = IntArray(levels.size) { it }
        for (level in 1..(levels.maxOrNull() ?: 0)) reverseRuns(order, levels, level)
        return order
    }

    /** [character] as it is drawn in right-to-left text, or read back from it: an opening bracket is drawn closing. */
    public fun mirrored(character: Char): Char = when (character) {
        '(' -> ')'
        ')' -> '('
        '[' -> ']'
        ']' -> '['
        '{' -> '}'
        '}' -> '{'
        '<' -> '>'
        '>' -> '<'
        '«' -> '»'
        '»' -> '«'
        else -> character
    }

    private fun reverseRuns(order: IntArray, levels: IntArray, level: Int) {
        var index = 0
        while (index < order.size) {
            if (levels[order[index]] < level) {
                index++
                continue
            }
            var end = index
            while (end < order.size && levels[order[end]] >= level) end++
            order.reverse(index, end)
            index = end
        }
    }

    /** A unit's class is that of its first strong character, a digit's where it has none, else it is neutral. */
    private fun classOf(unit: CharSequence): Int {
        var hasDigit = false
        var index = 0
        while (index < unit.length) {
            val character = unit[index]
            val next = unit.getOrNull(index + 1)
            if (character.isHighSurrogate() && next != null && next.isLowSurrogate()) {
                if (isStrongRtl(0x10000 + ((character.code - 0xD800) shl 10) + (next.code - 0xDC00))) return RIGHT
                index += 2
                continue
            }
            when {
                isStrongRtl(character.code) -> return RIGHT
                character.isLetter() -> return LEFT
                character.isDigit() -> hasDigit = true
            }
            index++
        }
        return if (hasDigit) NUMBER else NEUTRAL
    }

    private const val NEUTRAL = 0
    private const val LEFT = 1
    private const val RIGHT = 2
    private const val NUMBER = 3

    /** A number between right-to-left text in a left-to-right line, which is drawn inside that text's run. */
    private const val EMBEDDED_NUMBER = 4
}
