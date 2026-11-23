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
 * A page that draws its chords over the syllables they are played on and gives each a name spelled out for a screen
 * reader loses the drawing on copy: what remains is the chord names one row each, then the lyric with every chord's
 * spoken name where it stood (`(C sharp minor)These five words`). The rows of chords right above such a lyric are set
 * back over it as a chord row, each chord over its spoken name's place, so the converter reads the pair as any sheet.
 *
 * A spoken name is one only where its root is that of the next chord of the rows above, in order, which no lyric in
 * parentheses (`(A little bit)`) is likely to repeat; a lyric whose names do not all match stays as it is. Chords the
 * lyric names no place for are kept on a row of their own after it, and a line of nothing but names is the chord row alone.
 *
 * A page that sets each spoken name in a box of its own (one positioned for a screen reader only) has it on a line of its
 * own in the text the browser reads, the lyric cut into pieces between the names (`(E minor)`, `Today is `, `(G major)`,
 * `gonna be the day`). The pieces are put back together first ([joined]): each piece keeps the space that ended it, so a
 * word cut by a name (`Rea`, `(D major)`, `lized`) is one word again.
 */
internal object ChordSheetSpelledChords {
    fun lines(text: List<String>): List<String> {
        if (text.none { spokenName.containsMatchIn(it) }) return text
        val lines = joined(text)
        // The run of chord rows starting at each line, counted once from the end, so a long run is not read again from each
        // of its rows.
        val chordRuns = IntArray(lines.size + 1)
        for (index in lines.indices.reversed()) chordRuns[index] = if (ChordSheetConverter.isChordLine(lines[index])) chordRuns[index + 1] + 1 else 0
        val result = mutableListOf<String>()
        var index = 0
        while (index < lines.size) {
            val rows = chordRuns[index]
            val lyric = lines.getOrNull(index + rows)
            val set = if (rows > 0 && lyric != null) setOver(lines.subList(index, index + rows), lyric) else null
            if (set == null) {
                result += lines[index]
                index++
            } else {
                result += set
                index += rows + 1
            }
        }
        return result
    }

    /** The chord row and lyric [rows] and [lyric] stand for, or null where [lyric] names no chord of them in their order. */
    private fun setOver(rows: List<String>, lyric: String): List<String>? {
        val names = spokenName.findAll(lyric).filter(::isSpokenName).toList()
        if (names.isEmpty()) return null
        val chords = rows.flatMap { row -> ChordSheetChordWords.rowTokens(row).map { it.text }.filter { rootOf(it) != null } }
        if (chords.size < names.size) return null
        val text = StringBuilder()
        val chordRow = StringBuilder()
        var end = 0
        names.forEachIndexed { position, name ->
            val chord = chords[position]
            if (rootOf(chord) != spokenRoot(name)) return null
            text.append(lyric, end, name.range.first)
            // Chords named on consecutive syllables could touch; the later one moves right until a space parts them.
            val column = maxOf(text.length, if (chordRow.isEmpty()) 0 else chordRow.length + 1)
            while (chordRow.length < column) chordRow.append(' ')
            chordRow.append(chord)
            end = name.range.last + 1
        }
        text.append(lyric, end, lyric.length)
        val rest = chords.drop(names.size)
        return listOfNotNull(
            chordRow.toString(),
            text.toString().trimEnd().takeIf { it.isNotBlank() },
            rest.takeIf { it.isNotEmpty() }?.joinToString(" "),
        )
    }

    /**
     * [lines] with each lyric that follows a run of chord rows cut into pieces, a spoken name a line, joined into one line.
     * The pieces end where the row's chords have all been named and the piece after the last name is taken, or at the
     * first line that cannot be a piece: a blank line, a chord row, a heading, a line with no letter (a page's year
     * under its last chord row), or a second piece in a row, since a name always parts two of them.
     */
    private fun joined(lines: List<String>): List<String> {
        val result = mutableListOf<String>()
        var index = 0
        while (index < lines.size) {
            var rowEnd = index
            while (rowEnd < lines.size && ChordSheetConverter.isChordLine(lines[rowEnd])) rowEnd++
            val chords = lines.subList(index, rowEnd).sumOf { row -> ChordSheetChordWords.rowTokens(row).count { rootOf(it.text) != null } }
            val pieces = if (rowEnd > index) piecesAfter(lines, rowEnd, chords) else 0
            if (pieces < 2) {
                result += lines.subList(index, maxOf(rowEnd, index + 1))
                index = maxOf(rowEnd, index + 1)
            } else {
                result += lines.subList(index, rowEnd)
                result += lines.subList(rowEnd, rowEnd + pieces).joinToString("") { it.trimStart() }
                index = rowEnd + pieces
            }
        }
        return result
    }

    /** How many lines from [start] are one lyric's pieces and its spoken names, naming at most [chords] chords; 0 for none. */
    private fun piecesAfter(lines: List<String>, start: Int, chords: Int): Int {
        var names = 0
        var end = start
        var afterPiece = false
        while (end < lines.size) {
            val line = lines[end]
            if (isSpokenNameLine(line)) {
                if (names == chords) break
                names++
                afterPiece = false
            } else {
                if (afterPiece || !isPiece(line)) break
                afterPiece = true
            }
            end++
            if (names == chords && afterPiece) break
        }
        return if (names == 0) 0 else end - start
    }

    private fun isSpokenNameLine(line: String) = spokenName.matchEntire(line.trim())?.let { isSpokenName(it) } == true

    private fun isPiece(line: String) = line.isNotBlank() && line.any(Char::isLetter) && !ChordSheetConverter.isChordLine(line) &&
        ChordSheetHeadings.parse(line) == null && !spokenName.containsMatchIn(line)

    /** A bare letter in parentheses is as likely a lyric's `(A)`: a spoken name says sharp, flat, or what chord it is. */
    private fun isSpokenName(name: MatchResult) = name.groupValues[2].isNotEmpty() || name.groupValues[3].isNotEmpty()

    private fun rootOf(chord: String) = root.find(ChordSheetChordWords.ascii(chord))?.let { it.groupValues[1] + it.groupValues[2] }

    private fun spokenRoot(name: MatchResult) = name.groupValues[1] + when (name.groupValues[2].lowercase()) {
        "sharp" -> "#"
        "flat" -> "b"
        else -> ""
    }

    private val root = Regex("^([A-G])([#b]?)")
    private val spokenName = Regex(
        "\\(([A-G])(?: (sharp|flat))?((?: (?:major|minor|diminished|augmented|suspended|dominant|half|power|add|sus|seventh|sixth|" +
            "ninth|eleventh|thirteenth|second|fourth|fifth|[0-9]+(?:st|nd|rd|th)?|with|over|slash|bass|chord|[A-G](?: sharp| flat)?))*)\\)",
    )
}
