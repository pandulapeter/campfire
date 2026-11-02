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
    internal fun furniture(value: String) = value in listOf("|", "||", "|:", ":|", "||:", ":||", ":|:", "-", "/", "%", "(", ")", "...", "\u2026", ".") || value in noChord || ChordProRowMarks.repeatCount.matches(value)
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
        val words = tokens(text)
        val start = words.indexOfLast { it.text.startsWith('(') }.takeIf { start ->
            start > 0 && words.size - start <= 4 && words.last().text.endsWith(')') && words.drop(start).none { rowChord(it.text) || furniture(it.text) }
        } ?: words.size
        val row = mutableListOf<Token>()
        val notes = mutableListOf<Token>()
        var index = 0
        while (index < start) {
            val word = words[index]
            val end = if (isNote?.invoke(word) == true) {
                (index until start).takeWhile { isNote(words[it]) }.last()
            } else if (word.text.startsWith('(') && !rowChord(word.text)) {
                (index until minOf(start, index + 4)).firstOrNull { words[it].text.endsWith(')') }
                    ?.takeIf { end -> (index..end).none { rowChord(words[it].text) } }
            } else null
            if (end == null) { row += word; index++; continue }
            notes += Token(text.substring(word.index, words[end].index + words[end].text.length), word.index)
            index = end + 1
        }
        if (row.none { rowChord(it.text) } || !row.all { rowChord(it.text) || furniture(it.text) }) return null
        return ChordRow(row, text.substring(words.getOrNull(start)?.index ?: text.length).trim().ifEmpty { null }, notes)
    }

    /** A note printed among a row's chords, written as the annotation ChordPro sets over a lyric the way it sets a chord. */
    internal fun note(value: String) = "[$ANNOTATION_MARKER${value.removeSurrounding("(", ")").replace('[', '(').replace(']', ')').trim()}]"

    /** A chord of a chord row, where a star marks the one a footnote under the song explains (`C*`). */
    internal fun rowChord(value: String) = chord(value.removeSuffix("*").removeSuffix("*"))
    internal fun rowChordName(value: String) = ascii(value.removeSuffix("*").removeSuffix("*").removeSurrounding("(", ")"))

    internal fun isBareLetter(value: String) = value.length == 1 && value[0] in 'A'..'G'

    /** Text laid out in characters, whose columns are where its chords stand; a measured row's spacing says nothing. */
    internal fun isCharacterColumns(line: Rendered) = line.source.spans.all { it.isMonospace }

    /** A word, or a bar line or repeat sign standing for itself even where a chord is glued to it (`||:Am`). */
    internal val noChord = setOf("N.C.", "NC")
    private val token = Regex("\\|\\|:?|:\\|:|:\\|\\|?|\\|:?|[^\\s|]+")

    internal val parentheses = Regex("\\(([^()]+)\\)")
}
