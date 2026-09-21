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

    /** Transposes a chord name, keeping an optional chord's parentheses and its bass note. */
    fun transposeChord(name: String, semitones: Int, preferFlats: Boolean) =
        ChordProChordNames.rewriteNotes(name) { note -> transposeNote(note, semitones, preferFlats) }

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
                is ChordProLine.Grid -> yieldAll(line.tokens.filterIsInstance<GridToken.Chord>().map { it.name })
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
        val written = ChordProParser.parseAsWritten(text)
        val isGermanNotated = ChordProNotation.isGermanNotated(written)
        val song = if (isGermanNotated) ChordProNotation.fromGerman(written) else written
        val flats = preferFlats ?: prefersFlats(song, semitones)
        val transposeName = { name: String -> transposeChord(name, semitones, flats) }
        if (!isGermanNotated) return rewriteText(text, semitones, transposeName)
        val staysGermanNotated = writtenChordNames(song).any { name ->
            ChordProNotation.isGermanName(ChordProNotation.toGerman(transposeName(name)))
        }
        return rewriteText(text, semitones) { name ->
            val transposedName = transposeName(ChordProNotation.fromGerman(name))
            if (staysGermanNotated) ChordProNotation.toGerman(transposedName) else transposedName
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
                    if (directive.name == KEY) {
                        lines[index] = transposeKeyLine(rawLine, trimmedLine, directive.value, rename)
                    }
                }

                environment == TAB -> tabLineIndices += index
                environment == GRID -> lines[index] = transposeGridLine(rawLine, trimmedLine, rename)
                else -> lines[index] = rewriteLyricsLineChords(rawLine, rename)
            }
        }
        lines.transposeTab(tabLineIndices, semitones, rename) // An environment the file never closes.
        return ChordProSyntax.joinLines(lines, text)
    }

    /** Whether flats should be preferred when writing the chords of this song after the given transposition. */
    fun prefersFlats(song: ChordProSong, semitones: Int): Boolean {
        val key = song.metadata.key?.trim().orEmpty()
        val keyIndex = noteIndices[key.getOrNull(0)]
        if (keyIndex != null) {
            val accidental = accidentals[key.getOrNull(1)]
            val suffix = key.substring(if (accidental == null) 1 else 2)
            val rootName = flatNames[(keyIndex + (accidental ?: 0) + semitones).mod(NOTE_COUNT)]
            val isMinor = suffix.startsWith("m") && !suffix.startsWith("maj", ignoreCase = true)
            return if (isMinor) flatMinorKeys.contains(rootName + "m") else flatMajorKeys.contains(rootName)
        }
        var flats = 0
        var sharps = 0
        chordNames(song).forEach { name ->
            name.split(BASS_NOTE_SEPARATOR).forEach { part ->
                if (noteIndices.containsKey(part.getOrNull(0))) {
                    when (accidentals[part.getOrNull(1)]) {
                        1 -> sharps++
                        -1 -> flats++
                    }
                }
            }
        }
        return flats > sharps
    }

    private fun chordNames(song: ChordProSong) = song.blocks.asSequence()
        .filterIsInstance<ChordProBlock.Section>()
        .flatMap { it.lines }
        .flatMap { line ->
            when (line) {
                is ChordProLine.Lyrics -> line.chords.asSequence().filter { !it.isAnnotation }.map { it.name }
                is ChordProLine.Grid -> line.tokens.asSequence().filterIsInstance<GridToken.Chord>().map { it.name }
                else -> emptySequence()
            }
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
                if (token is GridToken.Chord) GridToken.Chord(rename(token.name)) else token
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
                if (isChord) append(BRACKET_OPEN).append(rename(content)).append(BRACKET_CLOSE)
                consumedUntil = bracket.range.last + 1
            }
            append(rawLine, consumedUntil, rawLine.length)
        }
    }

    private fun transposeGridLine(rawLine: String, trimmedLine: String, rename: (String) -> String): String {
        val matches = tokenRegex.findAll(trimmedLine).toList()
        val lastBarIndex = matches.indexOfLast { ChordProSyntax.isBar(it.value) }
        val body = buildString {
            var consumedUntil = 0
            matches.forEachIndexed { index, match ->
                val word = match.value
                val isChord = (lastBarIndex < 0 || index <= lastBarIndex) &&
                        !ChordProSyntax.isBar(word) && word != BEAT && word != REPEAT && word != DOUBLE_REPEAT
                append(trimmedLine, consumedUntil, match.range.first)
                append(if (isChord) rename(word) else word)
                consumedUntil = match.range.last + 1
            }
            append(trimmedLine, consumedUntil, trimmedLine.length)
        }
        return rawLine.replaceTrimmedPart(trimmedLine, body)
    }

    private fun transposeKeyLine(
        rawLine: String,
        trimmedLine: String,
        value: String?,
        rename: (String) -> String,
    ): String {
        val key = value?.trim().orEmpty()
        if (key.isEmpty()) return rawLine
        return rawLine.replaceTrimmedPart(trimmedLine, "{$KEY: ${rename(key)}}")
    }

    /** Swaps the trimmed part of a line for [replacement], keeping the surrounding whitespace. */
    private fun String.replaceTrimmedPart(trimmedLine: String, replacement: String): String {
        val start = length - trimStart().length
        return substring(0, start) + replacement + substring(start + trimmedLine.length)
    }

    private const val NOTE_COUNT = 12
    private const val BASS_NOTE_SEPARATOR = "/"
    private const val SOURCE_COMMENT = "#"
    private const val ANNOTATION_MARKER = "*"
    private const val KEY = "key"
    private const val BEAT = "."
    private const val REPEAT = "%"
    private const val DOUBLE_REPEAT = "%%"
    private const val TAB = "tab"
    private const val GRID = "grid"
    private const val BRACKET_OPEN = '['
    private const val BRACKET_CLOSE = ']'
    private val tokenRegex = Regex("\\S+")
    private val sharpNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val flatNames = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
    private val flatMajorKeys = setOf("F", "Bb", "Eb", "Ab", "Db", "Gb", "Cb")
    private val flatMinorKeys = setOf("Dm", "Gm", "Cm", "Fm", "Bbm", "Ebm", "Abm")
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
