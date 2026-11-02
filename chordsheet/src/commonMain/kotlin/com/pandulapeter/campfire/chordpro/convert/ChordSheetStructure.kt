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

import com.pandulapeter.campfire.chordpro.chords.ChordProChordNames
import com.pandulapeter.campfire.chordpro.syntax.ChordProDirectives

/**
 * Whether a text is a chord sheet at all, asked of a file whose name says nothing about what it holds. Stricter than
 * [ChordSheetConverter], which turns a text without structure into escaped prose: that is right for a `.txt` somebody
 * chose, and wrong for a README, a licence or a chapter of an e-book nobody asked about, which must not become a song.
 */
public object ChordSheetStructure {

    /**
     * True where the first [MAX_LINES] lines of [text] hold a directive ChordPro defines on a line of its own, chords
     * in brackets written against the syllable they are sung on (`[G]Hello`) on at least two lines, or chord rows over
     * lyrics: at least two chord rows, one of them with two chords or more, and one of them right above a line of
     * lyrics. A single token on a row of its own is as likely a list item (`- A`), a clause (`(a)`) or a sung syllable
     * (`La`), and a bracket followed by a parenthesis is a Markdown link, so neither counts.
     */
    public fun hasSongStructure(text: String): Boolean {
        val lines = text.lineSequence().take(MAX_LINES).toList()
        return lines.any { ChordProDirectives.isKnownDirective(it.trim()) } ||
            lines.count(::hasChordAgainstSyllable) >= 2 ||
            hasChordRowsOverLyrics(lines)
    }

    private fun hasChordAgainstSyllable(line: String) = ChordProDirectives.brackets(line).any { bracket ->
        bracket.content.isNotEmpty() && bracket.content.none(Char::isWhitespace) && isChordOrLatinName(bracket.content) &&
            line.getOrNull(bracket.range.last + 1)?.isLetter() == true
    }

    private fun hasChordRowsOverLyrics(lines: List<String>): Boolean {
        val rows = lines.indices.filter { lines[it].isNotBlank() && ChordSheetConverter.isChordLine(lines[it]) }
        return rows.size >= 2 &&
            rows.any { chordCount(lines[it]) >= 2 } &&
            rows.any { index -> lines.getOrNull(index + 1)?.let { it.isNotBlank() && !ChordSheetConverter.isChordLine(it) } == true }
    }

    private fun chordCount(line: String) = line.split(' ', '\t').count { word ->
        word.isNotEmpty() && isChordOrLatinName(word.replace('♯', '#').replace('♭', 'b').removeSurrounding("(", ")"))
    }

    private fun isChordOrLatinName(name: String) = ChordProChordNames.isChordName(name) || ChordProChordNames.latinExpanded(name) != null

    private const val MAX_LINES = 400
}
