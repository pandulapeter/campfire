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

import com.pandulapeter.campfire.chordpro.model.ChordProBlock
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.chordpro.model.GridToken

/**
 * Moves chords up or down by a number of semitones, either on the model or directly on the raw text.
 */
object ChordProTransposer {

    /**
     * Transposes a chord name, keeping an optional chord's parentheses and its bass note.
     *
     * A word that is not a chord name is returned as it was. Brackets are how a chart marks its parts as often as
     * they hold chords — `[Intro]`, `[Break]`, `[Chorus 2x]` — and every one of those that starts with a note letter
     * would otherwise be moved, with the `b` after an `E` eaten as a flat sign. [ChordProNotation.toGerman] has
     * asked the same question of the same names since it was written.
     */
    fun transposeChord(name: String, semitones: Int, preferFlats: Boolean): String {
        if (!ChordProChordNames.isChordName(name)) return name
        return ChordProChordNames.rewriteNotes(name) { note -> transposeNote(note, semitones, preferFlats) }
    }

    /**
     * Transposes every chord in the model (lyrics chords, grid chords, tabs; never annotations) and the key.
     *
     * @param preferFlats Forces the spelling of the accidentals; null leaves it to [prefersFlats], which is what the
     *   song itself asks for. Forced, it is worth doing even for no semitones at all: respelling the chords of a song
     *   that is not transposed is exactly what a reader who always wants flats (or sharps) is asking for.
     */
    fun transpose(song: ChordProSong, semitones: Int, preferFlats: Boolean? = null): ChordProSong {
        if (semitones == 0 && preferFlats == null) return song
        return transpose(song, semitones, preferFlats ?: prefersFlats(song, semitones))
    }

    private fun transpose(song: ChordProSong, semitones: Int, preferFlats: Boolean): ChordProSong {
        val rename = { name: String -> transposeChord(name, semitones, preferFlats) }
        return rewriteChords(
            song = song,
            rewriteTabLines = { lines -> ChordProTabTransposer.transpose(lines, semitones, rename) },
            rename = rename,
        )
    }

    /**
     * Applies [rename] to every chord of a song — its key, the chords over its lyrics and the chords of its grids,
     * never an annotation — and hands the raw lines of each run of tablature to [rewriteTabLines]. A run and not a
     * section: tablature is a way of writing lines down, so a single section may hold several of them with lyrics
     * in between, and each one is a fingerboard of its own to move.
     *
     * Both the transposition and [ChordProNotation] are written in terms of it, and they differ in exactly one thing:
     * what a tab is. To the transposition it is a fingerboard, so the frets move; to a notation it is a page, so only
     * the chord names above the staff are rewritten.
     */
    internal fun rewriteChords(
        song: ChordProSong,
        rewriteTabLines: (List<String>) -> List<String>,
        rename: (String) -> String,
    ): ChordProSong = song.copy(
        metadata = song.metadata.copy(key = song.metadata.key?.let(rename)),
        blocks = song.blocks.map { block ->
            if (block is ChordProBlock.Section) block.copy(lines = rewriteLines(block.lines, rewriteTabLines, rename)) else block
        },
    )

    /** Every name [rewriteChords] would hand to its rename, without rewriting anything. */
    internal fun writtenChordNames(song: ChordProSong): Sequence<String> = sequence {
        song.metadata.key?.let { yield(it) }
        song.blocks.asSequence().filterIsInstance<ChordProBlock.Section>().flatMap { it.lines }.forEach { line ->
            when (line) {
                is ChordProLine.Lyrics -> yieldAll(line.chords.filter { !it.isAnnotation }.map { it.name })
                is ChordProLine.Grid -> yieldAll(line.tokens.filterIsInstance<GridToken.Chord>().flatMap { ChordProSyntax.cellChords(it.name) })
                is ChordProLine.Tab -> yieldAll(ChordProTabTransposer.chordNames(listOf(line.text)))
                ChordProLine.Blank -> Unit
            }
        }
    }

    /**
     * Splits the lines into runs of tablature and everything else, and rewrites each the way it has to be.
     *
     * A blank line does not end a run: a tab environment keeps its blank lines, and the text path moves an environment
     * as one fingerboard however many systems it is written in, so the model has to make the same octave decision for
     * the same lines. Only the tab lines are handed to [rewriteTabLines]; the blanks go back where they were.
     */
    private fun rewriteLines(
        lines: List<ChordProLine>,
        rewriteTabLines: (List<String>) -> List<String>,
        rename: (String) -> String,
    ): List<ChordProLine> {
        if (lines.none { it is ChordProLine.Tab }) return lines.map { line -> rewriteLine(line, rename) }
        val rewritten = mutableListOf<ChordProLine>()
        var run = mutableListOf<ChordProLine>()
        fun flushRun() {
            if (run.isEmpty()) return
            val tabLines = rewriteTabLines(run.filterIsInstance<ChordProLine.Tab>().map { it.text }).iterator()
            rewritten += run.map { line -> if (line is ChordProLine.Tab) ChordProLine.Tab(tabLines.next()) else line }
            run = mutableListOf()
        }
        lines.forEach { line ->
            when {
                line is ChordProLine.Tab -> run += line
                line == ChordProLine.Blank && run.isNotEmpty() -> run += line
                else -> {
                    flushRun()
                    rewritten += rewriteLine(line, rename)
                }
            }
        }
        flushRun()
        return rewritten
    }

    /** Transposes raw ChordPro text in place, keeping its formatting and original notation. */
    fun transposeText(text: String, semitones: Int, preferFlats: Boolean? = null): String {
        if (semitones == 0 && preferFlats == null) return text
        val written = ChordProNotation.withLowercaseMinorsExpanded(ChordProParser.parseAsWritten(text))
        val isGermanNotated = ChordProNotation.isGermanNotated(written)
        val song = if (isGermanNotated) ChordProNotation.fromGerman(written) else written
        val flats = preferFlats ?: prefersFlats(song, semitones)
        val transposeName = { name: String -> transposeChord(name, semitones, flats) }
        if (!isGermanNotated) return rewriteText(text, semitones, keepingLowercaseMinors(transposeName))
        val staysGermanNotated = writtenChordNames(song).any { name ->
            ChordProNotation.isGermanName(ChordProNotation.toGerman(transposeName(name)))
        }
        return rewriteText(
            text,
            semitones,
            keepingLowercaseMinors { name ->
                val transposedName = transposeName(ChordProNotation.fromGerman(name))
                if (staysGermanNotated) ChordProNotation.toGerman(transposedName) else transposedName
            },
        )
    }

    /**
     * [rename] for a chord as the file writes it: a lowercase minor is spelled out for it and folded back afterwards,
     * so that the file keeps its own convention.
     */
    private fun keepingLowercaseMinors(rename: (String) -> String) = { name: String ->
        ChordProChordNames.lowercaseMinorExpanded(name)?.let { ChordProChordNames.lowercaseMinorFolded(rename(it)) } ?: rename(name)
    }

    /**
     * Where [offset] of [before] is in [after], for an [after] that [transposeText] made of [before]: an editor that
     * transposes the document under the caret keeps the caret next to the text it was next to, rather than at the
     * same character count, which every chord name that grew or shrank above it would have moved.
     *
     * A transposition keeps every line and changes nothing but chord names (and the frets of a tab), so the offset
     * keeps its line, and inside the line it keeps its place between the brackets; a line with no brackets to go by
     * (a `{key}`, a grid or a tab line) keeps the text in front of its first change and behind its last one.
     */
    fun transposedOffset(before: String, after: String, offset: Int): Int {
        val clamped = offset.coerceIn(0, before.length)
        if (before == after) return clamped
        val beforeStarts = ChordProSyntax.lineStartOffsets(before)
        val afterStarts = ChordProSyntax.lineStartOffsets(after)
        if (beforeStarts.size != afterStarts.size) return clamped.coerceAtMost(after.length)
        val line = lastLineStartingAtOrBefore(beforeStarts, clamped)
        val column = clamped - beforeStarts[line]
        val beforeLine = ChordProSyntax.splitLines(before)[line]
        val afterLine = ChordProSyntax.splitLines(after)[line]
        val afterLineStart = afterStarts[line]
        if (column > beforeLine.length) {
            // On the line break, or past the final one. The break is measured on the new text, since joinLines may
            // have given a file that mixed its endings a different separator.
            val limit = if (line + 1 < afterStarts.size) afterStarts[line + 1] - 1 else after.length
            return (afterLineStart + afterLine.length + (column - beforeLine.length)).coerceAtMost(limit)
        }
        return afterLineStart + transposedColumn(beforeLine, afterLine, column).coerceIn(0, afterLine.length)
    }

    private fun lastLineStartingAtOrBefore(starts: IntArray, offset: Int): Int {
        var low = 0
        var high = starts.lastIndex
        while (low < high) {
            val middle = (low + high + 1) / 2
            if (starts[middle] <= offset) low = middle else high = middle - 1
        }
        return low
    }

    private fun transposedColumn(before: String, after: String, column: Int): Int {
        if (before == after) return column
        val beforeBrackets = ChordProSyntax.brackets(before)
        val afterBrackets = ChordProSyntax.brackets(after)
        if (beforeBrackets.size == afterBrackets.size && beforeBrackets.isNotEmpty()) {
            var shift = 0
            beforeBrackets.forEachIndexed { index, bracket ->
                val oldRange = bracket.range
                val newRange = afterBrackets[index].range
                when {
                    column <= oldRange.first -> return column + shift
                    // Right before the "]" is after the whole chord name, which is where it stays.
                    column == oldRange.last -> return newRange.last
                    column < oldRange.last -> return (newRange.first + (column - oldRange.first)).coerceAtMost(newRange.last)
                    else -> shift = newRange.last - oldRange.last
                }
            }
            return column + shift
        }
        val prefix = before.commonPrefixWith(after).length
        val suffix = minOf(before.commonSuffixWith(after).length, minOf(before.length, after.length) - prefix)
        // The two ends meet only where the line merely grew at the caret (a key of C becoming C#), and a caret there
        // stays after what grew, the way it stays after the whole of a chord name that did.
        return when {
            column >= before.length - suffix -> after.length - (before.length - column)
            column <= prefix -> column
            else -> (after.length - suffix).coerceAtLeast(prefix)
        }
    }

    private fun rewriteText(text: String, semitones: Int, rename: (String) -> String): String {
        val lines = ChordProSyntax.splitLines(text).toMutableList()
        val tabLineIndices = mutableListOf<Int>() // The tab environment being collected: it is transposed as a whole.
        var environment: String? = null
        lines.forEachIndexed { index, rawLine ->
            val trimmedLine = rawLine.trim()
            val directive = if (trimmedLine.startsWith(SOURCE_COMMENT)) null else ChordProSyntax.matchDirective(trimmedLine)
            when {
                trimmedLine.startsWith(SOURCE_COMMENT) -> Unit
                directive != null -> if (!ChordProSyntax.hasSelectorSuffix(directive.name)) {
                    ChordProSyntax.startOfEnvironment(directive.name)?.let {
                        lines.transposeTab(tabLineIndices, semitones, rename)
                        environment = it.lowercase()
                    }
                    ChordProSyntax.endOfEnvironment(directive.name)?.let {
                        lines.transposeTab(tabLineIndices, semitones, rename)
                        environment = null
                    }
                    if (directive.name in ChordProSyntax.blockNames) {
                        // The parser cuts the section in two here, and each half of the tab is a run of its own in the model;
                        // moving them as one fingerboard would let the viewer and the editor disagree about the octave.
                        lines.transposeTab(tabLineIndices, semitones, rename)
                    }
                    (ChordProSyntax.standardMeta(directive) ?: directive).takeIf { it.name == KEY }?.value?.takeIf { it.isNotEmpty() }?.let { key ->
                        lines[index] = transposeKeyLine(rawLine, key, rename)
                    }
                }

                environment == TAB -> tabLineIndices += index
                environment == GRID -> lines[index] = transposeGridLine(rawLine, trimmedLine, rename)
                environment in ChordProSyntax.delegateEnvironments -> Unit
                else -> lines[index] = rewriteLyricsLineChords(rawLine, rename)
            }
        }
        lines.transposeTab(tabLineIndices, semitones, rename) // An environment the file never closes.
        return ChordProSyntax.joinLines(lines, text)
    }

    /** Whether flats should be preferred for the key this song arrives in after the transposition. */
    fun prefersFlats(song: ChordProSong, semitones: Int): Boolean {
        val names = writtenChordNames(song).toList()
        val key = song.metadata.key?.let(::keyOf)
            ?: names.firstNotNullOfOrNull { name -> name.takeIf(ChordProChordNames::isChordName)?.let(::keyOf) }
        return key?.transposedBy(semitones)?.prefersFlats ?: isWrittenInFlats(names)
    }

    private fun keyOf(name: String): Key? {
        val root = ChordProChordNames.notes(name.trim()).first()
        val noteIndex = noteIndices[root.getOrNull(0)] ?: return null
        val accidental = accidentals[root.getOrNull(1)]
        val suffix = root.substring(if (accidental == null) 1 else 2).trimStart()
        return Key((noteIndex + (accidental ?: 0)).mod(NOTE_COUNT), suffix.startsWith("m") && !suffix.startsWith("maj", ignoreCase = true))
    }

    private class Key(val tonic: Int, val isMinor: Boolean) {
        val prefersFlats get() = if (isMinor) flatNames[tonic] + "m" in flatMinorKeys else flatNames[tonic] in flatMajorKeys
        fun transposedBy(semitones: Int) = Key((tonic + semitones).mod(NOTE_COUNT), isMinor)
    }

    private fun isWrittenInFlats(names: List<String>): Boolean {
        var flats = 0
        var sharps = 0
        names.filter(ChordProChordNames::isChordName).flatMap(ChordProChordNames::notes).forEach { note ->
            if (noteIndices.containsKey(note.getOrNull(0))) {
                when (accidentals[note.getOrNull(1)]) {
                    1 -> sharps++
                    -1 -> flats++
                }
            }
        }
        return flats > sharps
    }

    /** Transposes the collected lines of one tab environment in place and starts collecting the next one. */
    private fun MutableList<String>.transposeTab(indices: MutableList<Int>, semitones: Int, rename: (String) -> String) {
        if (indices.isEmpty()) return
        val transposed = ChordProTabTransposer.transpose(indices.map { this[it] }, semitones, rename)
        indices.forEachIndexed { index, lineIndex -> this[lineIndex] = transposed[index] }
        indices.clear()
    }

    private fun rewriteLine(line: ChordProLine, rename: (String) -> String) = when (line) {
        is ChordProLine.Lyrics -> line.copy(
            chords = line.chords.map { chord ->
                if (chord.isAnnotation) chord else chord.copy(name = rename(chord.name))
            }
        )

        is ChordProLine.Grid -> line.copy(
            tokens = line.tokens.map { token ->
                if (token is GridToken.Chord) {
                    GridToken.Chord(ChordProSyntax.cellChords(token.name).joinToString(ChordProSyntax.GRID_CELL_CHORD_SEPARATOR, transform = rename))
                } else {
                    token
                }
            }
        )

        else -> line
    }

    private fun transposeNote(part: String, semitones: Int, preferFlats: Boolean): String {
        val noteIndex = noteIndices[part.getOrNull(0)] ?: return part
        val accidental = accidentals[part.getOrNull(1)]
        val suffixStartIndex = if (accidental == null) 1 else 2
        val transposedNoteIndex = (noteIndex + (accidental ?: 0) + semitones).mod(NOTE_COUNT)
        return (if (preferFlats) flatNames else sharpNames)[transposedNoteIndex] + part.substring(suffixStartIndex)
    }

    /** Applies [rename] to every `[chord]` of a raw line, leaving the annotations, the empty brackets and the text. */
    internal fun rewriteLyricsLineChords(rawLine: String, rename: (String) -> String): String {
        val brackets = ChordProSyntax.brackets(rawLine)
        if (brackets.isEmpty()) return rawLine
        return buildString(rawLine.length) {
            var consumedUntil = 0
            brackets.forEach { bracket ->
                val content = bracket.content.trim()
                val isChord = content.isNotEmpty() && !content.startsWith(ANNOTATION_MARKER)
                append(rawLine, consumedUntil, if (isChord) bracket.range.first else bracket.range.last + 1)
                if (isChord) {
                    // The spaces a file puts inside its brackets are its own formatting, and the transposition keeps it.
                    val leading = bracket.content.length - bracket.content.trimStart().length
                    append(BRACKET_OPEN)
                        .append(bracket.content, 0, leading)
                        .append(rename(content))
                        .append(bracket.content, bracket.content.trimEnd().length, bracket.content.length)
                        .append(BRACKET_CLOSE)
                }
                consumedUntil = bracket.range.last + 1
            }
            append(rawLine, consumedUntil, rawLine.length)
        }
    }

    private fun transposeGridLine(rawLine: String, trimmedLine: String, rename: (String) -> String): String {
        // The ranges and the tokens are zipped by index, which is only safe because both are the same list of words:
        // parseGridTokens reads the line through ChordProSyntax.words as well.
        val matches = ChordProSyntax.words(trimmedLine)
        val tokens = ChordProSyntax.parseGridTokens(trimmedLine)
        val body = buildString {
            var consumedUntil = 0
            matches.forEachIndexed { index, match ->
                append(trimmedLine, consumedUntil, match.range.first)
                append(
                    if (tokens[index] is GridToken.Chord) {
                        ChordProSyntax.cellChords(match.value).joinToString(ChordProSyntax.GRID_CELL_CHORD_SEPARATOR, transform = rename)
                    } else {
                        match.value
                    }
                )
                consumedUntil = match.range.last + 1
            }
            append(trimmedLine, consumedUntil, trimmedLine.length)
        }
        return rawLine.replaceTrimmedPart(trimmedLine, body)
    }

    /**
     * Renames the value of a key directive in place. Only the value's own characters are replaced, so the directive
     * keeps the spelling and the spacing the file gives it; the value is the last thing before the closing brace, give
     * or take whitespace, however the directive is written.
     */
    private fun transposeKeyLine(rawLine: String, key: String, rename: (String) -> String): String {
        val valueEnd = rawLine.substring(0, rawLine.trimEnd().lastIndex).trimEnd().length
        return rawLine.substring(0, valueEnd - key.length) + rename(key) + rawLine.substring(valueEnd)
    }

    /** Swaps the trimmed part of a line for [replacement], keeping the surrounding whitespace. */
    private fun String.replaceTrimmedPart(trimmedLine: String, replacement: String): String {
        val start = length - trimStart().length
        return substring(0, start) + replacement + substring(start + trimmedLine.length)
    }

    private const val NOTE_COUNT = 12
    private const val SOURCE_COMMENT = "#"
    private const val ANNOTATION_MARKER = "*"
    private const val KEY = "key"
    private const val TAB = "tab"
    private const val GRID = "grid"
    private const val BRACKET_OPEN = '['
    private const val BRACKET_CLOSE = ']'
    private val sharpNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val flatNames = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
    private val flatMajorKeys = setOf("C", "F", "Bb", "Eb", "Ab", "Db")
    private val flatMinorKeys = setOf("Dm", "Gm", "Cm", "Fm", "Bbm", "Ebm")
    private val noteIndices = mapOf(
        'C' to 0,
        'D' to 2,
        'E' to 4,
        'F' to 5,
        'G' to 7,
        'A' to 9,
        'B' to 11,
        'H' to 11, // Only a word that is not a chord name still gets here with an H: the parser has read the rest into B.
    )
    private val accidentals = mapOf(
        '#' to 1,
        'b' to -1,
        '♯' to 1, // ♯
        '♭' to -1, // ♭
    )
}
