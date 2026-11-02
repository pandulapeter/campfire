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

import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.ascii
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.chord
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.chordRow
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.isCharacterColumns
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.isPositioned
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.noChord
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.note
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.parentheses
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.rowChord
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.rowChordName
import com.pandulapeter.campfire.chordpro.convert.ChordSheetChordWords.tokens
import com.pandulapeter.campfire.chordpro.convert.ChordSheetConverter.isChordLine
import com.pandulapeter.campfire.chordpro.convert.ChordSheetText.clean
import com.pandulapeter.campfire.chordpro.edit.ChordProLiteralText
import com.pandulapeter.campfire.chordpro.syntax.ChordProVocabulary.ANNOTATION_MARKER
import kotlin.math.abs
import kotlin.math.roundToInt

/** How a chord row's chords are set into the lyric under it, or written on a line of their own. */
internal object ChordSheetChordPlacement {
    /**
     * A chord row with no lyric under it. Its playing note is written as an annotation, so that it stands on the row with
     * the chords rather than as a lyric under them; one after a lyric line stays text, since the line has words already.
     */
    internal fun bracketed(row: List<Token>, chordRow: ChordRow?) = (row + chordRow?.notes.orEmpty())
        .sortedBy { it.index }.joinToString(" ") {
        when {
            it in chordRow?.notes.orEmpty() -> note(it.text)
            rowChord(it.text) -> "[${rowChordName(it.text)}]"
            else -> it.text
        }
    } +
        chordRow?.annotation?.let { note ->
            val inner = note.removeSurrounding("(", ")").replace('[', '(').replace(']', ')').trim()
            " " + if (inner.isEmpty()) ChordProLiteralText.escape(note) else "[$ANNOTATION_MARKER$inner]"
        }.orEmpty()

    /**
     * A row of three or more chords typed one space apart over a lyric more than twice its length lost its columns
     * when it was copied, so placing its chords by them would set them all into the lyric's first word. It is written
     * above the lyric instead; two chords over a short line may well be meant where they stand.
     */
    internal fun isUnaligned(row: Rendered, tokens: List<Token>, lyric: Rendered) = isCharacterColumns(row) && !isPositioned(tokens) &&
        tokens.count { rowChord(it.text) } >= 3 && lyric.text.trim().length > row.text.trim().length * 2

    internal fun inline(line: Rendered, convertParentheses: Boolean): String {
        // Escape prose first, then insert only the chord tokens the classifier accepted.
        val replacements = mutableListOf<Triple<Int, Int, String>>()
        if (convertParentheses) parentheses.findAll(line.text).filter { chord(it.groupValues[1]) }.forEach {
            replacements += Triple(it.range.first, it.range.last + 1, ascii(it.groupValues[1]))
        }
        val styledRuns = mutableListOf<ChordSheet.Span>()
        var run: ChordSheet.Span? = null
        val runText = StringBuilder()
        fun flushRun() {
            run?.let { styledRuns += it.copy(text = runText.toString()) }
            run = null
            runText.clear()
        }
        for (span in line.source.spans) {
            if (!span.isBold && !span.isRaised) { flushRun(); continue }
            val previous = run
            if (previous == null || previous.isBold != span.isBold || previous.isRaised != span.isRaised ||
                abs(previous.end - span.start) >= span.size * 0.1) {
                flushRun()
                run = span
            } else run = previous.copy(end = span.end)
            runText.append(span.text)
        }
        flushRun()
        val chordLine = isChordLine(line.text)
        // Stacked chords are padded back over the glyphs before them, so positions are only searched by halving where
        // they never go back; anywhere else the linear scan is the one that finds the right glyph.
        val isMonotone = line.positions.indices.drop(1).all { line.positions[it - 1] <= line.positions[it] }
        for (span in styledRuns) if (!chordLine && chord(span.text.trim())) {
            val start = if (isMonotone) {
                firstIndex(line.positions.size) { line.positions[it] > span.start - 0.01 }
                    .takeIf { it < line.positions.size && line.positions[it] < span.start + 0.01 } ?: -1
            } else line.positions.indexOfFirst { abs(it - span.start) < 0.01 }
            if (start >= 0) replacements += Triple(start, start + clean(span.text).length, ascii(span.text.trim()))
        }
        val text = ChordProLiteralText.escape(line.text)
        return buildString {
            var cursor = 0
            for ((start, end, value) in replacements.sortedBy { it.first }) {
                // A replacement overlapping the one before it would rewrite text that is already a chord, so it is dropped.
                if (start < cursor || end > text.length || splitsSurrogate(text, start) || splitsSurrogate(text, end)) continue
                append(text, cursor, start)
                append("[$value]")
                cursor = end
            }
            append(text, cursor, text.length)
        }
    }

    /**
     * The lyric without the dashes the ChordPro app fills a chord's width with where the chord has no syllable of its
     * own (`tonight[Db][Eb7]` prints as "tonight---" under them): a run of two or more ending the line, under a chord.
     */
    private fun withoutChordFill(chords: Rendered, lyrics: Rendered): Rendered {
        val text = lyrics.text.trimEnd()
        val start = text.indexOfLast { it != '-' } + 1
        if (text.length - start < 2 || start > 0 && text[start - 1] == ' ') return lyrics
        val x = lyrics.positions.getOrNull(start) ?: return lyrics
        val chordPositions = chordRow(chords)?.tokens.orEmpty().filter { rowChord(it.text) }.mapNotNull { chords.positions.getOrNull(it.index) }
        if (chordPositions.none { it >= x - 0.01 }) return lyrics
        return lyrics.copy(
            text = text.substring(0, start),
            positions = lyrics.positions.copyOf(start),
            source = lyrics.source.copy(spans = lyrics.source.spans.filter { it.start < x - 0.01 }),
        )
    }

    internal fun merge(chords: Rendered, lyricLine: Rendered, convertParentheses: Boolean): String {
        val lyrics = withoutChordPadding(chords, withoutChordFill(chords, lyricLine))
        val insertions = mutableMapOf<Int, MutableList<String>>()
        val starts = tokens(lyrics.text).map { it.index }
        val positions = lyrics.positions
        // The binary searches below need positions and spans that never go back, which padding stacked chords breaks;
        // such a line keeps the linear searches, which are right for any order.
        val isMonotone = positions.indices.drop(1).all { positions[it - 1] <= positions[it] }
        val spans = lyrics.source.spans
        val spansSorted = spans.indices.drop(1).all { spans[it - 1].start <= spans[it].start }
        val suffixMin = positions.copyOf()
        for (index in suffixMin.lastIndex - 1 downTo 0) suffixMin[index] = minOf(suffixMin[index], suffixMin[index + 1])
        val prefixMaxEnd = DoubleArray(spans.size)
        for (index in spans.indices) prefixMaxEnd[index] = maxOf(spans[index].end, prefixMaxEnd.getOrNull(index - 1) ?: Double.NEGATIVE_INFINITY)
        var previous = 0
        val row = chordRow(chords)
        val notes = row?.notes.orEmpty()
        val lyricsEnd = lyrics.source.spans.lastOrNull { it.text.isNotBlank() }?.end
        // A chord is hung from its syllable by eye, or by the columns of a plain text, so the edge it hangs from is looked
        // for within an em of the lyric's type.
        val bidiTolerance = lyrics.source.spans.firstOrNull { it.pieces.isNotEmpty() }?.size ?: 1.0
        // A layout that measures a chord's offset apart from drawing the lyric's glyphs rounds the two differently, so a
        // chord meant for a glyph may start a hair before it, which is still the glyph and never the end of the one before.
        // Only a glyph drawn where its own span starts is placed that exactly; one spaced evenly through a longer span is
        // an estimate the rounding would only push further off.
        val rounding = (lyrics.source.spans.firstOrNull { it.text.isNotBlank() }?.size ?: 1.0) * GLYPH_ROUNDING
        val drawnStarts = lyrics.source.spans.map { it.start }.sorted()
        var isAttachedAtEnd = false
        // Bar lines and repeat counts mean nothing over a lyric, but "no chord" says where the playing stops.
        for (word in (row?.tokens.orEmpty().filter { rowChord(it.text) || it.text in noChord } + notes).sortedBy { it.index }) {
            // A right-to-left annotation is written in its reading order over its glyphs, its first character at its
            // right end: over a lyric read by its positions rather than by the edges of its glyphs, it stands where its
            // leftmost glyph does, as a left-to-right chord stands where its first one does.
            val x = if (lyrics.bidi != null) chords.positions.getOrNull(word.index) ?: 0.0 else {
                word.text.indices.mapNotNull { chords.positions.getOrNull(word.index + it) }.minOrNull() ?: 0.0
            }
            val after = firstIndex(suffixMin.size) { suffixMin[it] > x }
            var at = (after - 1).coerceAtLeast(0)
            positions.getOrNull(after)?.takeIf { it - x <= rounding && isDrawnStart(drawnStarts, it) }?.let { at = after }
            // A row holding right-to-left text runs both ways, so its chords are set where they stand rather than in the
            // order they are read off the chord row, which is left to right.
            val bidi = lyrics.bidi?.indexOf(chordStart = x, chordEnd = chordEnd(chords, word), length = lyrics.text.length, tolerance = bidiTolerance)
            if (bidi != null) {
                at = bidi.coerceAtMost(lyrics.text.length)
                if (bidi == lyrics.text.length) isAttachedAtEnd = true
            } else if (lyrics.positions.isEmpty()) at = 0
            else {
                val lastWidth = lyrics.source.spans.lastOrNull()?.let { (it.end - it.start) / it.text.length.coerceAtLeast(1) } ?: 1.0
                if (x >= lyrics.positions.last() + lastWidth - 0.01) {
                    at = lyrics.text.length
                    if (lyricsEnd != null && isOverEndPadding(lyrics.source.spans, lyricsEnd, x)) isAttachedAtEnd = true
                } else {
                    val nearest = if (isMonotone) {
                        val right = firstIndex(starts.size) { positions[starts[it]] >= x }
                        val left = if (right > 0) firstIndex(starts.size) { positions[starts[it]] >= positions[starts[right - 1]] } else -1
                        when {
                            left < 0 -> starts.getOrNull(right)
                            right == starts.size || abs(positions[starts[left]] - x) <= abs(positions[starts[right]] - x) -> starts[left]
                            else -> starts[right]
                        }
                    } else starts.minByOrNull { abs(positions[it] - x) }
                    nearest?.let { start ->
                        val position = positions[start]
                        val span = if (spansSorted) {
                            spans.getOrNull(firstIndex(spans.size) { prefixMaxEnd[it] > position })?.takeIf { it.start <= position }
                        } else spans.firstOrNull { position >= it.start && position < it.end }
                        val width = span?.let { (it.end - it.start) / it.text.length.coerceAtLeast(1) } ?: 1.0
                        if (abs(lyrics.positions[start] - x) <= width * 1.05 || lyrics.text.getOrNull(at) == ' ' && start == at + 1) at = start
                    }
                }
            }
            if (bidi == null) at = at.coerceAtLeast(previous)
            previous = at
            insertions.getOrPut(at) { mutableListOf() } += if (word in notes) note(word.text) else "[${rowChordName(word.text)}]"
        }
        var escaped = ChordProLiteralText.escape(withoutBidiPadding(lyrics, insertions))
        // A lyric printed a few columns in from the chord over it starts where that chord does, which is all its indent
        // says; written as it is, it would be sung after a pause of spaces. Two chords or more in the indent are played
        // before the words, so it is kept where they would otherwise run together.
        val indent = escaped.indexOfFirst { !it.isWhitespace() }
        if (indent > 0 && insertions.keys.count { it < indent } <= 1) {
            val moved = insertions.entries.sortedBy { it.key }.groupBy({ (it.key - indent).coerceAtLeast(0) }, { it.value })
            insertions.clear()
            moved.forEach { (at, values) -> insertions[at] = values.flatten().toMutableList() }
            escaped = escaped.substring(indent)
        }
        val merged = buildString {
            var carried: List<String> = emptyList()
            for (index in 0..escaped.length) {
                val due = insertions[index].orEmpty()
                if (splitsSurrogate(escaped, index)) carried = due
                else (carried + due).takeIf { it.isNotEmpty() }?.let { values ->
                    // A chord carried past a final emoji belongs to it, so only a chord beyond the lyrics is set apart.
                    if (index == escaped.length && carried.isEmpty() && isNotEmpty() && last() != ' ' && !isAttachedAtEnd) append(' ')
                    append(values.joinToString(if (index == escaped.length) " " else ""))
                    carried = emptyList()
                }
                if (index < escaped.length) append(escaped[index])
            }
        }
        // Inline conversion uses different offsets after insertion; convert those tokens in the merged string.
        val converted = if (convertParentheses) parentheses.replace(merged) { if (chord(it.groupValues[1])) "[${ascii(it.groupValues[1])}]" else it.value } else merged
        return converted + row?.annotation?.let { " " + ChordProLiteralText.escape(it) }.orEmpty()
    }

    /**
     * The lyric without the no-break spaces a layout pads a syllable with to make room for a wide chord, `to°°day` under
     * `Cmaj7 G`: a run of them that the chord before reaches into, or to within a space of, and that the next chord stands
     * in or starts right after holds no space of the words, so a word it cuts is joined back, and beside a space it adds
     * none. A no-break space that only separates words, `I°[G]come` in a table of a web page, has no chord reaching into it
     * and stays the space it is.
     */
    private fun withoutChordPadding(chords: Rendered, lyrics: Rendered): Rendered {
        if (lyrics.bidi != null || '\u00a0' !in lyrics.source.spans.joinToString("") { it.text }) return lyrics
        val isPadding = lyrics.text.indices.map { it in noBreakSpaces(lyrics) }
        val placed = chordRow(chords)?.tokens.orEmpty().filter { rowChord(it.text) }
            .mapNotNull { word -> chords.positions.getOrNull(word.index)?.let { it to chordEnd(chords, word) } }
        val dropped = mutableSetOf<Int>()
        var index = 0
        while (index < isPadding.size) {
            if (!isPadding[index]) { index++; continue }
            val first = index
            while (index < isPadding.size && isPadding[index]) index++
            val from = lyrics.positions[first]
            val to = lyrics.positions.getOrNull(index) ?: continue
            val size = lyrics.source.spans.firstOrNull { from >= it.start - 0.01 && from < it.end }?.size ?: continue
            val isPadded = placed.any { (start, _) -> start >= from - 0.01 && start <= to + size * PADDED_CHORD } &&
                placed.any { (start, end) -> start < from && end + size * CHORD_GAP > from }
            if (isPadded) dropped += first until index
        }
        if (dropped.isEmpty()) return lyrics
        val kept = lyrics.text.indices.filter { it !in dropped }
        return lyrics.copy(text = kept.map { lyrics.text[it] }.joinToString(""), positions = DoubleArray(kept.size) { lyrics.positions[kept[it]] })
    }

    /**
     * The text of a right-to-left row ([Rendered.bidi]) without the no-break spaces a layout pads a word with to make room
     * for the wide chord before the next chord set in it, `קט°°[C]נה`, with [insertions] moved onto what is left: a run
     * of them inside a word, a chord set on the letter after it and another before it in the row. [withoutChordPadding]
     * finds the same padding by where it is drawn, which a row written in its reading order over its glyphs does not keep.
     */
    private fun withoutBidiPadding(lyrics: Rendered, insertions: MutableMap<Int, MutableList<String>>): String {
        val text = lyrics.text
        val raw = lyrics.source.spans.joinToString("") { it.text }.takeIf { lyrics.bidi != null && clean(it).trimEnd() == text } ?: return text
        val dropped = mutableSetOf<Int>()
        var index = 0
        while (index < text.length) {
            if (raw[index] != '\u00a0') { index++; continue }
            val first = index
            while (index < text.length && raw[index] == '\u00a0') index++
            val isInWord = first > 0 && !raw[first - 1].isWhitespace() && index < text.length && !raw[index].isWhitespace()
            if (isInWord && insertions.containsKey(index) && insertions.keys.any { it < first }) dropped += first until index
        }
        if (dropped.isEmpty()) return text
        val moved = insertions.entries.associate { (at, values) -> at - dropped.count { it < at } to values }
        insertions.clear()
        insertions.putAll(moved)
        return text.filterIndexed { at, _ -> at !in dropped }
    }

    /** The characters of [lyrics] set as no-break spaces, which the reading cleaned to plain ones. */
    internal fun noBreakSpaces(lyrics: Rendered): Set<Int> = lyrics.text.indices.filterTo(mutableSetOf()) { index ->
        val position = lyrics.positions[index]
        // Glyphs set edge to edge overlap by a rounding, so the character is the last span starting at its position.
        lyrics.text[index] == ' ' && lyrics.source.spans.lastOrNull { position >= it.start - 0.01 && position < it.end }?.let { span ->
            val width = (span.end - span.start) / span.text.length.coerceAtLeast(1)
            span.text.getOrNull(((position - span.start) / width).roundToInt().coerceIn(0, span.text.length - 1)) == '\u00a0'
        } == true
    }

    /** Whether a chord at [x] past the lyric's last character, which ends at [end], stands over no-break spaces padding it. */
    private fun isOverEndPadding(spans: List<ChordSheet.Span>, end: Double, x: Double): Boolean {
        val padding = spans.filter { it.start >= end - 0.01 }.takeWhile { span -> span.text.isNotEmpty() && span.text.all { it == '\u00a0' } }
        return padding.isNotEmpty() && x <= padding.last().end + 0.01
    }

    /** Where the last character of [word] on the chord row [chords] ends. */
    private fun chordEnd(chords: Rendered, word: Token): Double {
        val last = (word.index + word.text.length - 1).coerceAtMost(chords.positions.lastIndex)
        val position = chords.positions.getOrNull(last) ?: return 0.0
        val span = chords.source.spans.lastOrNull { position >= it.start - 0.01 && position < it.end + 0.01 }
        // The span's width is shared by the characters render keeps of it.
        return position + (span?.let { (it.end - it.start) / clean(it.text).length.coerceAtLeast(1) } ?: 0.0)
    }

    /** Whether [position] is where a span of [starts] (sorted) begins, which only a glyph drawn on its own is exactly. */
    private fun isDrawnStart(starts: List<Double>, position: Double) =
        starts.getOrNull(firstIndex(starts.size) { starts[it] >= position - 0.001 })?.let { it <= position + 0.001 } == true

    /** An insertion or replacement must leave a supplementary character whole. */
    private fun splitsSurrogate(text: String, index: Int) = index > 0 && index < text.length &&
        text[index - 1].isHighSurrogate() && text[index].isLowSurrogate()

    /** The first true element of a monotone predicate, or the size when none matches. */
    private inline fun firstIndex(size: Int, matches: (Int) -> Boolean): Int {
        var low = 0
        var high = size
        while (low < high) {
            val middle = low + (high - low) / 2
            if (matches(middle)) high = middle else low = middle + 1
        }
        return low
    }

    /** How far before a glyph's start a chord still stands on it, as a share of the lyric's type size: far less than any glyph is wide. */
    private const val GLYPH_ROUNDING = 0.01

    /** How far past a run of no-break spaces a chord may start and still be the one it pads for, as a share of the lyric's type size. */
    private const val PADDED_CHORD = 0.25

    /** The least room a layout leaves between two chords of a row, as a share of the lyric's type size: about a space. */
    private const val CHORD_GAP = 0.3
}
