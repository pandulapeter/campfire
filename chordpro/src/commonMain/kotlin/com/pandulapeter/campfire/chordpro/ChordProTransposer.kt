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

    /** Transposes a single chord name; returns the input unchanged if it is not a chord (e.g. "N.C."). */
    fun transposeChord(name: String, semitones: Int, preferFlats: Boolean) = name
        .split(BASS_NOTE_SEPARATOR, limit = 2)
        .joinToString(BASS_NOTE_SEPARATOR) { transposeNote(it, semitones, preferFlats) }

    /** Transposes every chord in the model (lyrics chords, grid chords; never tabs or annotations) and the key. */
    fun transpose(song: ChordProSong, semitones: Int): ChordProSong {
        if (semitones == 0) return song
        val preferFlats = prefersFlats(song, semitones)
        return song.copy(
            metadata = song.metadata.copy(
                key = song.metadata.key?.let { transposeChord(it, semitones, preferFlats) }
            ),
            blocks = song.blocks.map { block ->
                if (block is ChordProBlock.Section) {
                    block.copy(lines = block.lines.map { line -> transposeLine(line, semitones, preferFlats) })
                } else {
                    block
                }
            }
        )
    }

    /** Transposes raw ChordPro text in place, keeping all formatting. Used by the editor's transpose action. */
    fun transposeText(text: String, semitones: Int): String {
        if (semitones == 0) return text
        val preferFlats = prefersFlats(ChordProParser.parse(text), semitones)
        var environment: String? = null
        return ChordProSyntax.splitLines(text).joinToString("\n") { rawLine ->
            val trimmedLine = rawLine.trim()
            val directive = if (trimmedLine.startsWith(SOURCE_COMMENT)) null else ChordProSyntax.matchDirective(trimmedLine)
            when {
                trimmedLine.startsWith(SOURCE_COMMENT) -> rawLine
                directive != null -> {
                    if (ChordProSyntax.hasSelectorSuffix(directive.name)) {
                        rawLine
                    } else {
                        ChordProSyntax.startOfEnvironment(directive.name)?.let { environment = it.lowercase() }
                        ChordProSyntax.endOfEnvironment(directive.name)?.let { environment = null }
                        if (directive.name == KEY) {
                            transposeKeyLine(rawLine, trimmedLine, directive.value, semitones, preferFlats)
                        } else {
                            rawLine
                        }
                    }
                }

                environment == TAB -> rawLine
                environment == GRID -> transposeGridLine(rawLine, trimmedLine, semitones, preferFlats)
                else -> transposeLyricsLine(rawLine, semitones, preferFlats)
            }
        }
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

    private fun transposeLine(line: ChordProLine, semitones: Int, preferFlats: Boolean) = when (line) {
        is ChordProLine.Lyrics -> line.copy(
            chords = line.chords.map { chord ->
                if (chord.isAnnotation) chord else chord.copy(name = transposeChord(chord.name, semitones, preferFlats))
            }
        )

        is ChordProLine.Grid -> line.copy(
            tokens = line.tokens.map { token ->
                if (token is GridToken.Chord) GridToken.Chord(transposeChord(token.name, semitones, preferFlats)) else token
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

    private fun transposeLyricsLine(rawLine: String, semitones: Int, preferFlats: Boolean) =
        ChordProSyntax.chordRegex.replace(rawLine) { match ->
            val content = match.groupValues[1].trim()
            if (content.isEmpty() || content.startsWith(ANNOTATION_MARKER)) {
                match.value
            } else {
                "[${transposeChord(content, semitones, preferFlats)}]"
            }
        }

    private fun transposeGridLine(rawLine: String, trimmedLine: String, semitones: Int, preferFlats: Boolean): String {
        val matches = tokenRegex.findAll(trimmedLine).toList()
        val lastBarIndex = matches.indexOfLast { ChordProSyntax.isBar(it.value) }
        val body = buildString {
            var consumedUntil = 0
            matches.forEachIndexed { index, match ->
                val word = match.value
                val isChord = (lastBarIndex < 0 || index <= lastBarIndex) &&
                        !ChordProSyntax.isBar(word) && word != BEAT && word != REPEAT && word != DOUBLE_REPEAT
                append(trimmedLine, consumedUntil, match.range.first)
                append(if (isChord) transposeChord(word, semitones, preferFlats) else word)
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
        semitones: Int,
        preferFlats: Boolean
    ): String {
        val key = value?.trim().orEmpty()
        if (key.isEmpty()) return rawLine
        return rawLine.replaceTrimmedPart(trimmedLine, "{$KEY: ${transposeChord(key, semitones, preferFlats)}}")
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
        'H' to 11 // German notation.
    )
    private val accidentals = mapOf(
        '#' to 1,
        'b' to -1,
        '♯' to 1, // ♯
        '♭' to -1 // ♭
    )
}
