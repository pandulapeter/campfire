/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.tabs

import com.pandulapeter.campfire.chordpro.chords.ChordProChordNames
import com.pandulapeter.campfire.chordpro.chords.ChordProNotation
import com.pandulapeter.campfire.chordpro.chords.ChordNotation
import com.pandulapeter.campfire.chordpro.tabs.ChordProTabTransposer.SEMITONES_IN_OCTAVE
import com.pandulapeter.campfire.chordpro.tabs.ChordProTabWrapper
import com.pandulapeter.campfire.chordpro.syntax.ChordProTokens

/**
 * The names of a staff's strings (`e|`, `Eb|`, `Mi|`): how they are read, for [ChordProTabRetuner], and how they are
 * written in another notation, for [ChordProNotation.convertText] and the parsing of a text typed in one.
 */
internal object ChordProStaffNames {

    /**
     * The lines of one tab environment with every string name of its staves read in [from] and written in [to], the way
     * the chords above them are: the editor's field names the strings in the reader's notation, and the file in the
     * standard one. Every staff line of a system keeps its bar in one column with the others, the lines above the staff
     * moving with them so that a chord stays over its note.
     *
     * Between the standard notation and the German one only the names whose note differs are renamed: a file's bare `B`
     * is shown as `H`, and a German `B` and `H` are saved as `Bb` and `B`. Every other name, a sign, a Latin name or a
     * file's own `H` included, reads the same note in both and is kept as written, so whatever the field shows is read
     * back as the notes the file meant, and a file whose `H` is saved as `B` keeps its tuning.
     *
     * In the other notations a name is respelled keeping its accidental and its case (see [respelled]). Each staff line
     * keeps its indentation relative to the others, the least indented one keeping its own, so the move the way back
     * takes is exactly the one the way there took and a file opened in another notation and saved again is the file it
     * was; read from the standard notation, a system that would not come back exactly is left with its names as
     * written. A system indented with tab characters, whose columns are not characters, is never renamed. A text typed
     * in another notation is always brought into the standard one, since that is what every file is written in.
     */
    fun respelledStaffNames(lines: List<String>, from: ChordNotation, to: ChordNotation): List<String> {
        if (from == to) return lines
        val isStaffLine = lines.map(ChordProTokens::isStaffLine)
        if (isStaffLine.none { it }) return lines
        val isGerman = setOf(from, to) == setOf(ChordNotation.STANDARD, ChordNotation.GERMAN)
        val rename: (String) -> String = if (isGerman) {
            { name -> germanRespelled(name, toGerman = to == ChordNotation.GERMAN) }
        } else {
            { name -> respelled(name, from, to) }
        }
        val respelled = lines.toMutableList()
        ChordProTabWrapper.systems(lines, isStaffLine).forEach { range ->
            val system = range.map { lines[it] }
            val staff = range.map { isStaffLine[it] }
            val moved = respelledSystem(system, staff, rename) ?: return@forEach
            if (!isGerman && from == ChordNotation.STANDARD && respelledSystem(moved, staff) { respelled(it, to, from) } != system) return@forEach
            range.forEachIndexed { line, index -> respelled[index] = moved[line] }
        }
        return respelled
    }

    /**
     * [lines] with the accidental of every string name drawn as the sign [ChordProNotation.withAccidentalSigns] draws a
     * chord's with (`Eb|` as `E♭|`). A sign takes the column its letter took, so nothing else moves.
     */
    fun withAccidentalSigns(lines: List<String>): List<String> = lines.map { line ->
        if (!ChordProTokens.isStaffLine(line)) return@map line
        val name = staffPrefix(line).name?.takeIf { it.length > 1 && noteOf(it) != null } ?: return@map line
        val start = line.indexOf(name)
        val signs = name.first() + name.substring(1).replace('#', '♯').replace('b', '♭')
        line.substring(0, start) + signs + line.substring(start + name.length)
    }

    /** The lines of one system, [isStaff] telling its staff lines from the rest, with its names [rename]d, or null where nothing changes. */
    private fun respelledSystem(lines: List<String>, isStaff: List<Boolean>, rename: (String) -> String): List<String>? {
        val staff = lines.indices.filter { isStaff[it] }
        if (staff.isEmpty() || lines.any { line -> line.takeWhile { it.isWhitespace() }.contains(TAB_CHARACTER) }) return null
        val names = staff.map { staffPrefix(lines[it]).name }
        val newNames = names.map { name -> name?.let(rename) }
        if (newNames == names) return null
        val indents = staff.map { index -> lines[index].takeWhile { it == ' ' }.length }
        val growths = staff.indices.map { line -> newNames[line].orEmpty().length - names[line].orEmpty().length }
        val shift = staff.indices.maxOf { line -> growths[line] - indents[line] } + indents.min()
        return lines.mapIndexed { index, text ->
            val line = staff.indexOf(index)
            if (line < 0) {
                text.shiftedBy(shift)
            } else {
                " ".repeat(indents[line] + shift - growths[line]) + newNames[line].orEmpty() + text.substring(indents[line] + names[line].orEmpty().length)
            }
        }
    }

    /** [this] with [columns] spaces put in front of it, or as many of its leading spaces taken off as it has, up to that. */
    fun String.shiftedBy(columns: Int) = when {
        columns > 0 && isNotBlank() -> " ".repeat(columns) + this
        columns < 0 -> drop(minOf(-columns, takeWhile { it == ' ' }.length))
        else -> this
    }

    /**
     * What opens a staff line: the name of its string (`Eb`, `e`, `D#`, `Mi`; a string's number, `1|`, names no note)
     * and the bar after it. [bodyStart] is where the music starts: after the bar, at the first dash after a name with no
     * bar (`e-----`, `e -----`), or after the indentation of a line that names nothing (`|-----`'s bar is music, the one a
     * staff starts with). [name] is the name as it is written, right after the indentation, where it names a note.
     */
    class StaffPrefix(val note: Int?, val bodyStart: Int, val name: String?)

    fun staffPrefix(line: String): StaffPrefix {
        var index = 0
        while (index < line.length && line[index].isWhitespace()) index++
        val indentation = index
        while (index < line.length && index - indentation < MAX_NAME_LENGTH && (line[index].isLetterOrDigit() || line[index] in ACCIDENTALS)) index++
        val name = line.substring(indentation, index)
        val note = noteOf(name)
        var barIndex = index
        while (barIndex < line.length && line[barIndex] == ' ') barIndex++
        return when {
            name.isNotEmpty() && line.getOrNull(barIndex) == BAR -> StaffPrefix(note = note, bodyStart = barIndex + 1, name = name.takeIf { note != null })
            // Without a bar only a note followed by the staff (`e-----`, `e -----`) names a string: a staff that starts
            // with a fret is music.
            note != null && line.getOrNull(barIndex) == '-' -> StaffPrefix(note = note, bodyStart = barIndex, name = name)
            line.getOrNull(indentation) == BAR -> StaffPrefix(note = null, bodyStart = indentation + 1, name = null)
            else -> StaffPrefix(note = null, bodyStart = indentation, name = null)
        }
    }

    /**
     * The pitch class a string's name stands for, in the standard notation, the German or the Latin one, or null. A `B`
     * is the B natural here, as it is in every file: a text typed in German has its names read into the standard
     * notation before a staff is read (see [respelledStaffNames]).
     */
    private fun noteOf(name: String): Int? {
        val standard = standardSpellingOf(name) ?: return null
        val base = when (standard.first()) {
            'C' -> 0
            'D' -> 2
            'E' -> 4
            'F' -> 5
            'G' -> 7
            'A' -> 9
            'B', 'H' -> 11
            else -> return null
        }
        val accidental = when (standard.substring(1)) {
            "" -> 0
            "#", "♯" -> 1
            "b", "♭" -> -1
            else -> return null
        }
        return (base + accidental).mod(SEMITONES_IN_OCTAVE)
    }

    /** A string's name in any notation, capitalized and with a Latin name spelled in letters, or null where it is a number. */
    private fun standardSpellingOf(name: String): String? {
        if (name.isEmpty() || name.first().isDigit()) return null
        val capitalized = name.replaceFirstChar { it.uppercaseChar() }
        return ChordProChordNames.latinExpanded(capitalized) ?: capitalized
    }

    /**
     * A string's [name], written in [from], as [to] writes it, keeping the accidental the writer chose (`Eb` is not made
     * a `D#`) and a lowercase first letter, which tells the higher of two strings that share a name apart. An `H` is
     * the B natural in any notation; a `B` is one too, but in a text written in German, where it is the B flat, as the
     * German chords are read. A name that is no note is returned as it is.
     */
    fun respelled(name: String, from: ChordNotation, to: ChordNotation): String {
        val standard = standardSpellingOf(name)?.takeIf { noteOf(it) != null } ?: return name
        val accidental = when (val written = standard.substring(1)) {
            "♯" -> "#"
            "♭" -> "b"
            else -> written
        }
        val spelled = when {
            standard.first() == 'H' -> "B$accidental"
            standard.first() == 'B' && accidental.isEmpty() && from == ChordNotation.GERMAN -> "Bb"
            else -> standard.first() + accidental
        }
        val shown = ChordProNotation.shownName(spelled, to)
        return if (name.first().isLowerCase()) shown.lowercase() else shown
    }

    /**
     * A string's [name] between a file and a German field, renamed only where German reads it as another note: the bare
     * B, which is the B flat in German. A lowercase first letter is kept.
     */
    private fun germanRespelled(name: String, toGerman: Boolean): String {
        val renamed = when (name.uppercase()) {
            "B" -> if (toGerman) "H" else "Bb"
            "H" -> if (toGerman) return name else "B"
            else -> return name
        }
        return if (name.first().isLowerCase()) renamed.lowercase() else renamed
    }

    private const val MAX_NAME_LENGTH = 4
    private const val BAR = '|'
    private const val TAB_CHARACTER = '\t'
    private val ACCIDENTALS = setOf('#', '♯', '♭')
}
