/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.formats.document

import com.pandulapeter.campfire.chordpro.convert.ChordSheet
import com.pandulapeter.campfire.data.formats.document.PdfLineLayout.Line

/**
 * The blank lines a PDF draws only as space: a gap over [ABSOLUTE_GAP] ems whatever the document's leading, or one that
 * stands out from the step the document sets its lines at, so a tightly set songbook's stanza breaks are seen too. The
 * latter only parts two runs of [STANZA_LINES] lines or more each set at the usual step: a line or two set apart is a
 * field or a note of its own (a header's credit wrapped onto a second line, a chord diagram's name over its frets), which
 * blank lines would only cut off from what it belongs to.
 */
internal object PdfParagraphs {
    /**
     * The document's [pages] of lines as the converter reads them, a blank line wherever a gap stands out. Steps are
     * measured in ems of the larger type of the two lines, so a heading's bigger type does not read as a gap.
     */
    fun lines(pages: List<List<Line>>): List<List<ChordSheet.Line>> {
        val usual = usualStep(pages)
        return pages.map { lines ->
            val columns = lines.mapNotNull { it.column }.distinct()
            val result = mutableListOf<ChordSheet.Line>()
            fun isGap(index: Int) = usual != null && step(lines[index - 1], lines[index]) > usual * RELATIVE_GAP
            fun isRun(index: Int) = index in 1..lines.lastIndex && lines[index - 1].column === lines[index].column &&
                lines[index].y > lines[index - 1].y && !isGap(index)
            fun partsStanzas(index: Int) = isGap(index) && (1..<STANZA_LINES).all { isRun(index - it) && isRun(index + it) }
            lines.forEachIndexed { index, line ->
                val previous = lines.getOrNull(index - 1)
                if (previous != null && (step(previous, line) > ABSOLUTE_GAP || partsStanzas(index))) result += ChordSheet.Line(emptyList())
                result += ChordSheet.Line(line.spans, baseline = line.y, column = line.column?.let(columns::indexOf))
            }
            result
        }
    }

    /**
     * The step most of the document's lines are set at, in ems, or null for a document of too few lines to tell. A chord
     * sheet alternates two steps by design (a chord row sits closer to its lyric than to the lyric before it), so the
     * median can fall on the shorter one and a lyric followed by a chord row would read as a stanza break; a quantile
     * above the half lands on the longer one while gaps stay under two lines in five, as they do in any song.
     */
    private fun usualStep(pages: List<List<Line>>): Double? {
        val steps = pages.flatMap { lines ->
            lines.zipWithNext()
                .filter { (previous, line) -> previous.column === line.column && line.y > previous.y }
                .map { (previous, line) -> step(previous, line) }
        }.sorted()
        return if (steps.size < MINIMUM_STEPS) null else steps[(steps.size * USUAL_QUANTILE).toInt()]
    }

    private fun step(previous: Line, line: Line) = (line.y - previous.y) / ((line.spans + previous.spans).maxOfOrNull { it.size } ?: 12.0)

    private const val ABSOLUTE_GAP = 2.2
    private const val RELATIVE_GAP = 1.4
    private const val USUAL_QUANTILE = 0.6
    private const val MINIMUM_STEPS = 8
    private const val STANZA_LINES = 3
}
