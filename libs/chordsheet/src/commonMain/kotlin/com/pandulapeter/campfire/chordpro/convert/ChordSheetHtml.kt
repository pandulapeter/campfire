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

import com.pandulapeter.campfire.chordpro.language.WritingDirection
import com.pandulapeter.campfire.chordpro.language.writingDirectionOf

/**
 * The chord sheet in an HTML fragment a browser copied, for a page whose plain text loses where its chords stand: one
 * that draws its chord names with CSS (`content: attr(data-chord)`), whose generated text a browser leaves out of the
 * plain text it copies while its HTML keeps the attribute, and one that sets each chord over its syllable in a box of its
 * own, whose rows a browser copies onto lines of their own while its HTML keeps the boxes.
 */
public object ChordSheetHtml {

    /**
     * [html] as a plain-text chord sheet, every chord in a row of its own over its lyric, at the column of the character
     * it stands before; null where the fragment draws no chord either way. A chord is an element with no text in it whose
     * `data-*` attribute holds a chord name; with several such attributes, the first one written is the chord, since a
     * page that keeps a transposed spelling for display beside the song's own tends to name the song's own first, and
     * Campfire transposes by itself. Or it is the chord row of an inline box over a lyric fragment ([ChordSheetHtmlCells]).
     * Block elements and `<br>` end lines, an empty block is a blank line, and a `<p>` is followed by one, as a browser's
     * own plain-text copy has them; nothing the fragment points at is fetched. A song read from the right is written as a
     * right-to-left display shows a plain-text sheet ([ChordSheetHtmlRightToLeft]).
     *
     * The result is meant for [ChordSheetConverter], as any pasted text is: chord rows over lyrics rather than inline
     * ChordPro, which the converter would take as finished and leave without its headings and sections.
     */
    public fun chordSheetText(html: String): String? {
        // Windows puts a description of the fragment's offsets before its HTML, which AWT may hand over with it.
        val fragment = if (html.startsWith("Version:")) html.substring(html.indexOf('<').coerceAtLeast(0)) else html
        val lines = ChordSheetHtmlReader(fragment).read()
        if (lines.none { it.chords.isNotEmpty() }) return null
        val isRightToLeft = writingDirectionOf(lines.joinToString("\n") { it.text }) == WritingDirection.RIGHT_TO_LEFT
        val rows = if (isRightToLeft) ChordSheetHtmlRightToLeft.rendered(lines) else lines.flatMap(::rendered)
        return rows.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }.joinToString("\n")
    }

    /** The chord row (where the line has chords) and the lyric, each chord moved right only to keep a space after the last. */
    private fun rendered(line: ChordSheetHtmlReader.Line): List<String> {
        val lyric = line.text.toString().replace('\u00a0', ' ').trimEnd()
        if (line.chords.isEmpty()) return listOf(lyric)
        val row = StringBuilder()
        line.chords.forEach { (index, chord) ->
            val column = if (row.isEmpty()) index else maxOf(index, row.length + 1)
            while (row.length < column) row.append(' ')
            row.append(chord)
        }
        return if (lyric.isBlank()) listOf(row.toString()) else listOf(row.toString(), lyric)
    }

    /** A chord name an attribute may hold: a standard or a Latin one, capitalized, as a page draws it. */
    internal fun chordIn(value: String): String? = ChordSheetChordWords.ascii(value.trim()).takeIf {
        it.firstOrNull()?.isUpperCase() == true && ChordSheetChordWords.isChordOrLatinName(it)
    }
}
