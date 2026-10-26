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

import com.pandulapeter.campfire.chordpro.convert.PlacedLine.Companion.text

/**
 * The lines heading a page that are printed in parts, the way the ChordPro app sets each line of its header as a left,
 * a centre, and a right part (the title in the middle, the subtitle at the right margin). Only the lines above the
 * first chord row are read, and only a gap of [GAP] ems splits one: no line of text or of a song leaves one that wide.
 */
internal object ChordSheetHeaderParts {
    private const val GAP = 3.0
    private const val MAX_LINES = 5

    /** The page's lines, each header line printed in parts split into one line per part, all but the first flagged. */
    fun split(lines: List<ChordSheet.Line>): List<PlacedLine> {
        var read = 0
        var inHeader = true
        return lines.flatMap { line ->
            val text = text(line.spans)
            if (text.isNotEmpty()) read++
            if (read > MAX_LINES || ChordSheetConverter.isChordRow(text)) inHeader = false
            if (!inHeader || line.spans.any { it.isMonospace }) listOf(PlacedLine(line)) else parts(line)
        }
    }

    private fun parts(line: ChordSheet.Line): List<PlacedLine> {
        val parts = mutableListOf(mutableListOf<ChordSheet.Span>())
        var previous: ChordSheet.Span? = null
        for (span in line.spans) {
            if (span.text.isBlank()) { parts.last() += span; continue }
            if (previous != null && span.start - previous.end >= previous.size * GAP) parts += mutableListOf<ChordSheet.Span>()
            parts.last() += span
            previous = span
        }
        return parts.mapIndexed { index, spans -> PlacedLine(ChordSheet.Line(spans, line.isHeading), isHeaderPart = index > 0) }
    }
}
