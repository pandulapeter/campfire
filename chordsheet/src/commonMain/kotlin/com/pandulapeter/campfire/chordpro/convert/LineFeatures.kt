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
 */
internal data class LineFeatures(
    val heading: ChordSheetHeadings.Heading?,
    val metadata: Pair<String, String>?,
    val headerRow: List<Pair<String, String>>?,
    val bodyRow: List<Pair<String, String>>?,
    val look: ChordSheetLook?,
    val isSung: Boolean,
    val isLabel: Boolean,
) {
    companion object {
        /** What each of a song's [lines] says on its own, read once for every decision about the song. */
        fun of(lines: List<Rendered>): List<LineFeatures> {
            val bodySize = ChordSheetProfile.of(lines.map { it.source }).bodySize
            // A label heads a section, never the song (the line it opens with is its title's place), and a section has
            // lines under it: a smaller line closing a cover is its subtitle.
            val first = lines.indexOfFirst { it.text.isNotBlank() }
            val last = lines.indexOfLast { it.text.isNotBlank() }
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
                    isSung = line.marks?.isContinuation != true && sungWord.containsMatchIn(line.text),
                    isLabel = index != first && index != last && metadata == null && headerRow == null && bodyRow == null && isLabel(line, bodySize),
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

        private val sungWord = Regex("\\p{L}{2}")
    }
}
