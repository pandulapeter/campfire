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
 * Verses numbered at the start of their first line (`1. A hundred days`), the way many songbooks number them instead of
 * heading them: each is a verse under its number, which is no word of the lyric. A song numbers its verses 1, 2, … in
 * order, so only lyrics that do so, two at least, are taken for numbered; a lyric that happens to open with a number
 * (`1. place`) stays a lyric.
 */
internal object ChordSheetVerseNumbers {

    /** The lyrics of [lines] (of the kinds in [kinds]) that open a numbered verse, by index, with their numbers. */
    fun of(lines: List<Rendered>, kinds: List<LineKind>): Map<Int, String> {
        val numbered = lines.indices.filter { kinds[it] == LineKind.LYRIC }.mapNotNull { at ->
            number.find(lines[at].text)?.let { at to it.groupValues[1] }
        }
        if (numbered.size < 2 || numbered.map { it.second.toInt() } != (1..numbered.size).toList()) return emptyMap()
        // A verse goes on past its first line, which a numbered list of one line each (a contents page) does not.
        val starts = numbered.map { it.first }.toSet()
        val isStanza = starts.all { at ->
            ((at + 1)..lines.lastIndex).firstOrNull { kinds[it] != LineKind.CHORD }?.let { kinds[it] == LineKind.LYRIC && it !in starts } == true
        }
        return if (isStanza) numbered.toMap() else emptyMap()
    }

    /** [line] without the number it opens with. */
    fun withoutNumber(line: Rendered): Rendered {
        val start = number.find(line.text)?.range?.last?.plus(1) ?: return line
        return line.copy(text = line.text.substring(start), positions = line.positions.copyOfRange(start, line.positions.size))
    }

    private val number = Regex("^\\s*([1-9][0-9]?)\\.\\s+(?=\\S)")
}
