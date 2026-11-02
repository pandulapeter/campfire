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
 * Section labels printed in a column of their own left of the song, the way the ChordPro app prints a section's
 * `label` (`V1`, `Ch1`, `Br`, `A1`) on the first line of the section. A label is short, is no chord, and stands a gap
 * apart from the line it is printed on; it is one only where every line between the page's first and last label starts
 * right of all of them, so that a lyric or a chord row set against the left edge never loses its first word.
 */
internal object ChordSheetMarginLabels {
    /** A section label written with the abbreviations margin labels use, which no heading in running text would be. */
    private val abbreviations = mapOf(
        "v" to "verse", "vs" to "verse", "vrs" to "verse",
        "c" to "chorus", "ch" to "chorus", "cho" to "chorus", "chor" to "chorus", "ref" to "chorus", "refr" to "chorus",
        "b" to "bridge", "br" to "bridge", "brg" to "bridge", "pc" to "bridge", "pre" to "bridge",
    )
    private const val MAX_LENGTH = 12

    private class Label(
        val spans: List<ChordSheet.Span>,
        val rest: List<ChordSheet.Span>,
        val start: Double,
        val end: Double,
        val contentStart: Double,
        val size: Double,
    )

    /**
     * The page's lines with every margin label split off its line into one of its own, flagged true. The label goes
     * above the chord row printed over its line, so that the row still meets the lyric it is set over.
     */
    fun split(placed: List<PlacedLine>): List<PlacedLine> {
        val lines = placed.map { it.line }
        val shaped = lines.map(::label)
        // A line of the body may be shaped like a label too (a chord row opening with "(x)"), so only those starting
        // left of the body are taken, and the body's edge is measured again between the ones that are.
        val labels = edge(lines, shaped)?.let { edge -> shaped.map { label -> label?.takeIf { it.start < edge } } } ?: return placed
        if (labels.count { it != null } < 2) return placed
        val edge = edge(lines, labels) ?: return placed
        if (labels.any { it != null && (it.end > edge - it.size * 0.5 || it.contentStart < edge - it.size * 0.5) }) return placed
        val result = mutableListOf<PlacedLine>()
        placed.forEachIndexed { index, line ->
            val label = labels[index]
            if (label == null) { result += line; return@forEachIndexed }
            val heading = PlacedLine(line.line.copy(spans = label.spans, isHeading = true), isMarginLabel = true)
            val previous = result.lastOrNull()
            if (previous != null && !previous.isMarginLabel && ChordSheetConverter.isChordRow(text(previous.line.spans))) result.add(result.lastIndex, heading)
            else result += heading
            result += line.copy(line = line.line.copy(spans = label.rest))
        }
        return result
    }

    /** The ChordPro section a margin label opens: its heading word's, an abbreviation's, or a verse named by the label. */
    fun type(label: String): String = ChordSheetHeadings.parse(label)?.type
        ?: abbreviations[label.trim().trimEnd { it.isDigit() || it.isWhitespace() || it == '.' }.lowercase()]
        ?: "verse"

    /** Where the body starts: the leftmost text of the lines without a label, between the first label and the last. */
    private fun edge(lines: List<ChordSheet.Line>, labels: List<Label?>): Double? {
        val first = labels.indexOfFirst { it != null }.takeIf { it >= 0 } ?: return null
        return (first..labels.indexOfLast { it != null }).filter { labels[it] == null }
            .mapNotNull { index -> lines[index].spans.firstOrNull { it.text.isNotBlank() }?.start }
            .minOrNull()
    }

    private fun label(line: ChordSheet.Line): Label? {
        val ink = line.spans.withIndex().filter { it.value.text.isNotBlank() }
        val boundary = ink.zipWithNext().indexOfFirst { (before, after) -> after.value.start - before.value.end >= before.value.size * 0.5 }
        if (boundary < 0) return null
        val lastOfLabel = ink[boundary]
        val spans = line.spans.subList(0, lastOfLabel.index + 1)
        val rest = line.spans.subList(ink[boundary + 1].index, line.spans.size)
        val text = text(spans)
        if (text.length > MAX_LENGTH || text.split(' ').size > 2 || text.none { it.isLetterOrDigit() }) return null
        // A label may be a chord's name (C for the chorus), but a chord row whose first chord stands in the margin, over
        // a lyric's pickup, is chords all along the line.
        if (ChordSheetConverter.isChordLine(text) && ChordSheetConverter.isChordLine(text(rest))) return null
        return Label(spans, rest, ink.first().value.start, lastOfLabel.value.end, ink[boundary + 1].value.start, lastOfLabel.value.size)
    }
}
