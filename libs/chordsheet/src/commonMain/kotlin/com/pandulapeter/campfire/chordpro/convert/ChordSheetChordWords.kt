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
import com.pandulapeter.campfire.chordpro.syntax.ChordProRowMarks
import com.pandulapeter.campfire.chordpro.syntax.ChordProVocabulary.ANNOTATION_MARKER

/** What the converter takes for a chord, and which rows of a sheet are rows of chords. */
internal object ChordSheetChordWords {
    internal fun tokens(text: String): List<Token> = token.findAll(text).map { Token(it.value, it.range.first) }.toList()

    /**
     * The words of a row that may be one of chords, a chord held with a run of dashes (`Dm7-----`, `Am----Am7`) read as
     * the chords it holds, each where it stands: the dashes draw how long it rings, and no chord name holds two in a row.
     * Two chords joined by one dash (`Em-G`), half a bar each, are read the same way, but only where every piece is a
     * chord: a word such as `D-Day`, or a chord name that spells a flat fifth with one (`Cm7-5`), stays whole.
     */
    internal fun rowTokens(text: String): List<Token> = tokens(text).flatMap { word ->
        if ('-' !in word.text || rowChord(word.text)) return@flatMap listOf(word)
        val pieces = if ("--" in word.text) held.findAll(word.text).map { Token(it.value.trimEnd('-'), word.index + it.range.first) }.toList() else listOf(word)
        val chords = pieces.flatMap(::joinedChords)
        if (chords.isNotEmpty() && chords.all { rowChord(it.text) }) chords else listOf(word)
    }

    /** The chords [piece] joins with single dashes, each where it stands; [piece] itself where it is no such pair. */
    private fun joinedChords(piece: Token): List<Token> {
        if (rowChord(piece.text) || '-' !in piece.text || piece.text.split('-').any(String::isEmpty)) return listOf(piece)
        return joined.findAll(piece.text).map { Token(it.value, piece.index + it.range.first) }.toList()
    }

    private val held = Regex("[^-]+(?:-(?!-)[^-]*)*")
    private val joined = Regex("[^-]+")
    internal fun ascii(value: String) = value.replace('\u266f', '#').replace('\u266d', 'b')
    internal fun chord(value: String): Boolean {
        val name = ascii(value).removeSurrounding("(", ")")
        return isChordOrLatinName(name) ||
            name.firstOrNull() in 'a'..'h' && ChordProChordNames.isChordName(name.replaceFirstChar { it.uppercase() })
    }

    /**
     * Whether [name] is a chord in the standard notation or in the Latin one, which a Spanish, French or Italian sheet
     * is written in; the import brings it into the standard notation afterwards, with the rest of the song.
     */
    internal fun isChordOrLatinName(name: String) = ChordProChordNames.isChordName(name) || ChordProChordNames.latinExpanded(name) != null
    internal fun isBareLatinNote(value: String) = ascii(value).removeSurrounding("(", ")").let { ChordProChordNames.latinNoteLength(it) == it.length }
    internal fun isLatinChordBeyondNote(value: String) = ascii(value).removeSurrounding("(", ")").let { ChordProChordNames.latinExpanded(it) != null && !isBareLatinNote(it) }
    internal fun isPositioned(row: List<Token>) = row.zipWithNext().any { (a, b) -> b.index - (a.index + a.text.length) >= 2 }
    internal fun furniture(value: String) = value in listOf("|", "||", "|:", ":|", "||:", ":||", ":|:", "-", "/", "%", "(", ")", "...", "\u2026", ".", STRUM_MARK) ||
        value in noChord || ChordProRowMarks.repeatCount.matches(value)

    /**
     * A backslash a sheet ends a chord row with, or sets among its chords, to mark a strum or a chord let ring (`F  \`).
     * It is no chord and no beat, so the row is chords all the same and the mark is left out of it, every chord keeping
     * its column.
     */
    private const val STRUM_MARK = "\\"
    internal fun chordTokens(line: Rendered): List<Token>? = chordRow(line)?.tokens

    /**
     * A sheet that prints a song's annotations (`[*twice]`) in italics among a row's upright chords makes a run of italic
     * words there a note wherever it stands, in parentheses or not; a chord name or a bar line in italics is no note, since
     * some sheets set every chord in italics, and so is a row set in italics throughout.
     */
    internal fun chordRow(line: Rendered): ChordRow? = chordRow(
        text = line.text,
        isNote = if (!hasItalicsBesideUpright(line)) null else { word ->
            !rowChord(word.text) && !furniture(word.text) && word.text.indices.all { offset ->
                val x = line.positions.getOrNull(word.index + offset)
                x != null && line.source.spans.any { it.isItalic && x >= it.start && x < it.end }
            }
        },
    )

    private fun hasItalicsBesideUpright(line: Rendered) =
        line.source.spans.any { it.isItalic && it.text.isNotBlank() } && line.source.spans.any { !it.isItalic && it.text.isNotBlank() }

    internal fun chordRow(text: String, isNote: ((Token) -> Boolean)? = null): ChordRow? {
        val words = rowTokens(text)
        val start = words.indexOfLast { it.text.startsWith('(') }.takeIf { start ->
            start > 0 && words.size - start <= 4 && words.last().text.endsWith(')') && words.drop(start).none { rowChord(it.text) || furniture(it.text) }
        } ?: (words.size - 1).takeIf { last ->
            // A note in brackets ends a row, never sits among its chords: a repeat count, `Am7 D7 G7 C [2x]`.
            last > 0 && words[last].text.let { it.length > 2 && it.startsWith('[') && it.endsWith(']') && !rowChord(it) && !furniture(it) }
        } ?: words.size
        val row = mutableListOf<Token>()
        val notes = mutableListOf<Token>()
        var index = 0
        while (index < start) {
            val word = words[index]
            if (word.text == STRUM_MARK) { index++; continue }
            val end = if (isNote?.invoke(word) == true) {
                (index until start).takeWhile { isNote(words[it]) }.last()
            } else if (word.text.startsWith('(') && !rowChord(word.text)) {
                (index until minOf(start, index + 4)).firstOrNull { words[it].text.endsWith(')') }
                    ?.takeIf { end -> (index..end).none { rowChord(words[it].text) } }
                    // A repeat count ending the row whose parenthesis was never closed, `Am F C (x2`, is the same note.
                    ?: index.takeIf { it == start - 1 && ChordProRowMarks.repeatCount.matches(word.text) }
            } else null
            if (end == null) { row += word; index++; continue }
            notes += Token(text.substring(word.index, words[end].index + words[end].text.length), word.index)
            index = end + 1
        }
        if (row.none { rowChord(it.text) } || !row.all { rowChord(it.text) || furniture(it.text) }) return null
        return ChordRow(row, text.substring(words.getOrNull(start)?.index ?: text.length).trim().ifEmpty { null }, notes)
    }

    /** A note printed among a row's chords, written as the annotation ChordPro sets over a lyric the way it sets a chord. */
    internal fun note(value: String) = "[$ANNOTATION_MARKER${value.removePrefix("(").removeSuffix(")").replace('[', '(').replace(']', ')').trim()}]"

    /** A chord of a chord row, where a star marks the one a footnote under the song explains (`C*`). */
    internal fun rowChord(value: String) = chord(value.removeSuffix("*").removeSuffix("*"))
    internal fun rowChordName(value: String) = ascii(value.removeSuffix("*").removeSuffix("*").removeSurrounding("(", ")"))

    internal fun isBareLetter(value: String) = value.length == 1 && value[0] in 'A'..'G'

    /** Text laid out in characters, whose columns are where its chords stand; a measured row's spacing says nothing. */
    internal fun isCharacterColumns(line: Rendered) = line.source.spans.all { it.isMonospace }

    internal val noChord = setOf("N.C.", "NC")

    /**
     * A word, or a bar line or repeat sign standing for itself even where a chord is glued to it (`||:Am`). A final bar
     * (`|.`) and a volta (`|1`, `:|2>`) are one only where a space or the line's end follows them, which a glued chord or
     * a tab's frets never are.
     */
    private val token = Regex("\\|\\.(?=\\s|$)|:?\\|\\d+>?(?=\\s|$)|\\|\\|:?|:\\|:|:\\|\\|?|\\|:?|[^\\s|]+")

    internal val parentheses = Regex("\\(([^()]+)\\)")
}
