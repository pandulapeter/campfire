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

import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.chordRow
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.rowChord
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.rowChordName

/**
 * The list of the chords a song plays that a chord site prints beside it, one a line (`Am`, `C`, `F/C` under one
 * another), each name once. A single chord on a line is as likely a bar of the song, so a run is a list only where it
 * is corroborated: three chords or more, none named twice, every one played elsewhere in the song, standing before the
 * song's first row of chords or after its last, under no heading (an `Outro:` played one chord a line), and before a
 * blank line or the song's end where it heads the song, since a chord over a lyric is that lyric's. The fret a diagram
 * starts at, printed between the names (`2fr`), goes with the list, and so does a short line right above it naming
 * chords in a language of the app ([chordWords], `Chords in the song`, `Akkordok a dalban`), the list's label.
 */
internal object ChordSheetChordList {

    fun withoutList(lines: List<Rendered>): List<Rendered> {
        val rows = lines.map { line -> line.text.takeIf { it.isNotBlank() }?.let { chordRow(line) } }
        val songRows = rows.indices.filter { index -> (rows[index]?.tokens?.count { rowChord(it.text) } ?: 0) >= 2 }
        val first = songRows.firstOrNull() ?: return lines
        val last = songRows.last()
        fun single(index: Int) = rows[index]?.takeIf { it.annotation == null && it.notes.isEmpty() }?.tokens?.singleOrNull()?.takeIf { rowChord(it.text) }
        val dropped = mutableSetOf<Int>()
        var start = 0
        while (start < lines.size) {
            if (single(start) == null) { start++; continue }
            var end = start
            while (end + 1 < lines.size && (single(end + 1) != null || fret.matches(lines[end + 1].text.trim()))) end++
            while (single(end) == null) end--
            val run = start..end
            val names = run.mapNotNull(::single).map { name(it.text) }
            val played = rows.indices.filter { it !in run }.flatMap { index -> rows[index]?.tokens.orEmpty().filter { rowChord(it.text) }.map { name(it.text) } }.toSet()
            val above = (start - 1 downTo 0).firstOrNull { lines[it].text.isNotBlank() }
            val isBefore = end < first && lines.getOrNull(end + 1)?.text?.isBlank() != false
            if (names.size >= MIN_CHORDS && names.distinct().size == names.size && played.containsAll(names) && (isBefore || start > last) &&
                above?.let { ChordSheetHeadings.parse(lines[it].text) } == null
            ) {
                dropped += run
                above?.takeIf { it == start - 1 && isLabel(lines[it].text) }?.let { dropped += it }
            }
            start = end + 1
        }
        return if (dropped.isEmpty()) lines else lines.filterIndexed { index, _ -> index !in dropped }
    }

    /** A chord's name as it is compared, a German `H` read as the `B` a list beside the song may name it by. */
    private fun name(value: String) = rowChordName(value).replace('H', 'B')

    /** A short line naming chords in a language of the app, which labels a list of chords or of their shapes. */
    fun isLabel(text: String): Boolean {
        val words = text.trim().removeSuffix(":").lowercase().split(' ').filter { it.isNotEmpty() }
        return words.size <= MAX_LABEL_WORDS && words.any { it in chordWords }
    }

    /** The word for chords in each language of the app, as its strings name the Chords screen. */
    private val chordWords = setOf("chords", "akordy", "akkorde", "acordes", "accords", "\u05d0\u05e7\u05d5\u05e8\u05d3\u05d9\u05dd", "akkordok", "accordi", "acorduri")

    private const val MAX_LABEL_WORDS = 5
    private const val MIN_CHORDS = 3
    private val fret = Regex("^[0-9]{1,2}\\s*fr\\.?$", RegexOption.IGNORE_CASE)
}
