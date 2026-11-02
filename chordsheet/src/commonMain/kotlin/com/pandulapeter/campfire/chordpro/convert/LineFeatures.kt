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

import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.chordTokens
import com.pandulapeter.campfire.chordpro.convert.ChordSheetConverter.labelledRow
import com.pandulapeter.campfire.chordpro.convert.ChordSheetConverter.metadata
import kotlin.math.abs

/**
 * What a line of a song says on its own, read once: every decision about the song (its kinds, its header, its sections)
 * reads these rather than parsing the line again.
 *
 * @property heading The heading the line reads as, ambiguous or not ([ChordSheetHeadings.parse]).
 * @property metadata The field and value the line names, where it is one labelled value.
 * @property headerRow The values the line names as a row of labelled values, read as the header reads them where the
 *   line is in its reach ([ChordSheetProfile.isInHeader]), else as the body does.
 * @property bodyRow The values the line names as a row of labelled values read anywhere in the song.
 * @property look How the line's lyric is set, null for a line with nothing printed.
 * @property isSung Whether the line holds a sung word, which a tab's string or a stray accidental does not, and which the
 *   rest of a line Campfire's export wrapped is not counted as.
 * @property isLabel Whether the line is set as a section's label is in many layouts: a few words alone on their line in
 *   bold, smaller than the song's body, which neither a lyric nor a title is set in. Its words need not be a heading's
 *   (`V1`, `Main riff`): the type says what the line is, so it opens the section its words name, a verse where they name
 *   none. A labelled value is read as one rather than as a label.
 * @property isWrapped Whether the line is the rest of the lyric above it, which a narrow column wrapped onto a row of its
 *   own ([wrapped]).
 */
internal data class LineFeatures(
    val heading: ChordSheetHeadings.Heading?,
    val metadata: Pair<String, String>?,
    val headerRow: List<Pair<String, String>>?,
    val bodyRow: List<Pair<String, String>>?,
    val look: ChordSheetLook?,
    val isSung: Boolean,
    val isLabel: Boolean,
    val isWrapped: Boolean,
) {
    companion object {
        /** What each of a song's [lines] says on its own, read once for every decision about the song. */
        fun of(lines: List<Rendered>): List<LineFeatures> {
            val bodySize = ChordSheetProfile.of(lines.map { it.source }).bodySize
            // A label heads a section, never the song (the line it opens with is its title's place), and a section has
            // lines under it: a smaller line closing a cover is its subtitle.
            val first = lines.indexOfFirst { it.text.isNotBlank() }
            val last = lines.indexOfLast { it.text.isNotBlank() }
            val isWrapped = wrapped(lines, bodySize)
            return lines.mapIndexed { index, line ->
                val metadata = metadata(line.text)
                val headerRow = labelledRow(line.text, isHeader = ChordSheetProfile.isInHeader(index))
                val bodyRow = labelledRow(line.text, isHeader = false)
                LineFeatures(
                    heading = ChordSheetHeadings.parse(line.text),
                    metadata = metadata,
                    headerRow = headerRow,
                    bodyRow = bodyRow,
                    look = ChordSheetLook.of(line.source, line.positions.getOrNull(line.text.indexOfFirst { c -> !c.isWhitespace() })),
                    isSung = line.marks?.isContinuation != true && !isWrapped[index] && sungWord.containsMatchIn(line.text),
                    isLabel = index != first && index != last && metadata == null && headerRow == null && bodyRow == null && isLabel(line, bodySize),
                    isWrapped = isWrapped[index],
                )
            }
        }

        private fun isLabel(line: Rendered, bodySize: Double): Boolean {
            val spans = line.source.spans.filter { it.text.isNotBlank() }
            return spans.isNotEmpty() && line.text.any { it.isLetter() } && line.text.trim().split(' ').count { it.isNotEmpty() } <= LABEL_WORDS &&
                spans.all { it.isBold && !it.isItalic && !it.isMonospace && it.size < bodySize && !ChordSheetProfile.isSameSize(it.size, bodySize) } &&
                chordTokens(line) == null
        }

        private const val LABEL_WORDS = 4

        /**
         * Whether each line is the rest of the lyric above it, wrapped by a narrow column: in a column filled to its gutter,
         * a row starting where that lyric does and in its type, whose first word, or the chord over it where that is wider,
         * would not have fit after it before the column's edge, and opening without a capital, since a line of its own would
         * open with one, unless the lyric was broken at a space it kept ([isBrokenAtSpace]): a name or a capitalised word
         * wrapped onto the next row goes on the line all the same. A chord row between the two is the wrapped row's own.
         */
        private fun wrapped(lines: List<Rendered>, bodySize: Double): List<Boolean> {
            // Campfire's export marks the rows it wraps itself ([CampfireMarks.isContinuation]).
            if (lines.none { (it.source.column ?: 0) > 0 } || lines.any { it.marks != null }) return lines.map { false }
            val isChordRow = lines.map { chordTokens(it) != null }
            // A column's text wraps where it is filled to its gutter, to within a few ems of the body's type of where the next
            // column starts, and the last column's wraps where the one before it does. Its edge is then as far as its text
            // reaches, a title in a larger type included, which the gutter and the edge's slack are not measured in.
            val filled = lines.filter { it.text.isNotBlank() && it.source.column != null }.groupBy { it.source.column!! }
            val starts = filled.mapValues { (_, column) -> column.minOf(::start) }
            val reaches = filled.mapValues { (_, column) -> column.maxOf(::end) }
            val columns = starts.keys.sorted()
            val isFilled = columns.zipWithNext { column, next ->
                reaches.getValue(column) > starts.getValue(next) - bodySize * GUTTER
            }
            val edges = columns.filterIndexed { at, _ -> isFilled.getOrNull(at) ?: isFilled.getOrNull(at - 1) ?: false }
                .associateWith { reaches.getValue(it) + bodySize * REACH }
            return lines.indices.map { index ->
                val line = lines[index]
                val above = if (index > 0 && isChordRow[index - 1]) index - 2 else index - 1
                val lyric = lines.getOrNull(above)
                if (line.text.isBlank() || isChordRow[index] || lyric == null || lyric.text.isBlank() || isChordRow[above] || lyric.source.column != line.source.column) {
                    return@map false
                }
                val start = line.text.indexOfFirst { !it.isWhitespace() }
                val textEnd = line.text.indexOf(' ', start).takeIf { it > 0 }?.let { line.positions[it] } ?: end(line)
                // The first word's chord goes with it, and takes its own width where it is wider than the word.
                val wordEnd = if (index > 0 && isChordRow[index - 1]) maxOf(textEnd, chordReach(lines[index - 1], line.positions[start], textEnd)) else textEnd
                val size = line.source.spans.firstOrNull { it.text.isNotBlank() }?.size ?: 0.0
                val spaced = end(lyric) + size * WORD_GAP + wordEnd - line.positions[start]
                val opening = line.text.firstOrNull { it.isLetter() }
                abs(line.positions[start] - lyric.positions[lyric.text.indexOfFirst { !it.isWhitespace() }]) < POSITION && spaced > (edges[line.source.column] ?: Double.MAX_VALUE) &&
                    (opening?.isUpperCase() != true || isBrokenAtSpace(lyric)) && type(lyric) == type(line)
            }
        }

        /** How far the chords of [row] standing from [from] to [to] reach, [to] where none does. */
        private fun chordReach(row: Rendered, from: Double, to: Double) = chordTokens(row).orEmpty()
            .filter { row.positions.getOrNull(it.index)?.let { x -> x >= from - 0.01 && x < to } == true }
            .maxOfOrNull { token ->
                val last = row.positions[(token.index + token.text.length - 1).coerceAtMost(row.positions.lastIndex)]
                row.source.spans.lastOrNull { last >= it.start - 0.01 && last < it.end + 0.01 }?.end ?: last
            } ?: to

        /**
         * Whether [line] ends in the one space a soft wrap broke it at, which a PDF keeps on the row it breaks and a line
         * ending of its own does not carry; a run of spaces is padding rather than a break.
         */
        private fun isBrokenAtSpace(line: Rendered) = line.source.spans.joinToString("") { it.text }
            .let { it.length > 1 && it.last().isWhitespace() && !it[it.length - 2].isWhitespace() }

        private fun start(line: Rendered) = line.source.spans.first { it.text.isNotBlank() }.start

        private fun end(line: Rendered) = line.source.spans.filter { it.text.isNotBlank() }.maxOfOrNull { it.end } ?: 0.0

        private fun type(line: Rendered) = line.source.spans.filter { it.text.isNotBlank() }.map { Triple(it.isBold, it.isItalic, it.isMonospace) }.toSet()

        /**
         * A word's gap, the widest gutter of a column whose text wraps, and how far its edge may stand past the furthest its
         * text reaches, as shares of the type's size; and how far apart two rows may start and still start together.
         */
        private const val WORD_GAP = 0.25
        private const val GUTTER = 3.0
        private const val REACH = 0.2
        private const val POSITION = 1.0

        private val sungWord = Regex("\\p{L}{2}")
    }
}
