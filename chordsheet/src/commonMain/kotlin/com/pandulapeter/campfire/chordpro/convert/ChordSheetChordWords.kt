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
     * Campfire's export ([Rendered.marks]) prints a song's annotations (`[*twice]`) in italics among a row's bold chords, so
     * there a run of italic words is a note wherever it stands, in parentheses or not. A line set in italics throughout
     * is all notes, so it is never taken for chords, unless the row under it continues its line
     * ([CampfireMarks.isContinuedBelow]): it is then the chord row of a lyric's continuation, or an annotation opening a line,
     * printed alone over the padding its width needs where a narrow column wraps the lyric right after that padding.
     * There a word is a chord only where it is printed in the chords' type, bold under the title's size, or in a tab's
     * monospaced chord line, so that a lyric `a b c d` and a title `A` stay what they are; where the lyrics' size is
     * unknown, the text alone decides.
     */
    internal fun chordRow(line: Rendered, isCampfire: Boolean = line.marks != null): ChordRow? = chordRow(
        text = line.text,
        allowsNotesOnly = isCampfire && line.marks?.isContinuedBelow == true,
        isNote = if (!isCampfire) null else { word ->
            word.text.indices.all { offset ->
                val x = line.positions.getOrNull(word.index + offset)
                x != null && line.source.spans.any { it.isItalic && x >= it.start && x < it.end }
            }
        },
        isChord = if (!isCampfire || (line.marks?.bodySize ?: 0.0) <= 0.0) null else { word ->
            word.text.indices.all { offset ->
                val x = line.positions.getOrNull(word.index + offset)
                x != null && line.source.spans.any {
                    (it.isMonospace || it.isBold && it.size < line.marks!!.bodySize * ChordSheetCampfireLayout.TITLE_SIZE_RATIO) && x >= it.start - 0.01 && x < it.end
                }
            }
        },
    )

    internal fun chordRow(
        text: String,
        isNote: ((Token) -> Boolean)? = null,
        allowsNotesOnly: Boolean = false,
        isChord: ((Token) -> Boolean)? = null,
    ): ChordRow? {
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
        if (allowsNotesOnly && row.isEmpty() && notes.isNotEmpty()) return ChordRow(row, null, notes)
        if (row.none { rowChord(it.text) } || !row.all { rowChord(it.text) || furniture(it.text) }) return null
        if (isChord != null && row.any { rowChord(it.text) && !isChord(it) }) return null
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
