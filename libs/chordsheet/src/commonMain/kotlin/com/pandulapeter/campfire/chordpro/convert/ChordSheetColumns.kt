/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.convert

/**
 * The columns of a line of a chord sheet as a display sets them in characters, one string each: a character a column, a
 * surrogate pair one, a letter's marks in its column (a display draws them over it, unless they follow a space), and any
 * whitespace one space. Both right-to-left readers count by it, so that a chord stands over the same syllable whether
 * the sheet came as plain text or as a page's boxes.
 */
internal object ChordSheetColumns {

    /**
     * The columns of [line]: with [expandsTabs], a tab as the spaces up to the next stop of eight, as a plain-text
     * display sets it. [columnOf], where given, is filled with the column each character of [line] is in.
     */
    fun of(line: String, expandsTabs: Boolean, columnOf: IntArray? = null): List<String> {
        val units = mutableListOf<String>()
        var index = 0
        while (index < line.length) {
            val character = line[index]
            val next = line.getOrNull(index + 1)
            when {
                expandsTabs && character == '\t' -> repeat(8 - units.size % 8) { units += " " }
                character.isWhitespace() || character == '\u00a0' -> units += " "
                character.isHighSurrogate() && next != null && next.isLowSurrogate() -> {
                    units += "$character$next"
                    columnOf?.set(index, units.lastIndex)
                    index++
                }
                units.isNotEmpty() && units.last() != " " && character.isMark() -> units[units.lastIndex] += character
                else -> units += character.toString()
            }
            columnOf?.set(index, units.lastIndex)
            index++
        }
        return units
    }

    private fun Char.isMark() = category == CharCategory.NON_SPACING_MARK || category == CharCategory.ENCLOSING_MARK
}
