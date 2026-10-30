/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.print

import com.pandulapeter.campfire.chordpro.language.WritingDirection
import com.pandulapeter.campfire.chordpro.language.isStrongRtl
import com.pandulapeter.campfire.chordpro.model.ChordProLine
import com.pandulapeter.campfire.data.model.preferences.PrintSettings
import com.pandulapeter.campfire.presentation.ui.songLayout.PADDING_MARK
import com.pandulapeter.campfire.presentation.ui.songLayout.padLyricsToFitChords
import kotlin.math.ceil

/**
 * The rows of a line of lyrics with its chords, at most [capacity] tall each, laid out in its paragraph direction (its
 * lyric style's): a right-to-left row is drawn from the right edge of its column, and its chords are kept apart and
 * wrapped leftwards, an overflowing one starting the next chord line at the right edge. A row that mixes directions
 * takes its chords' places from the renderer's layout ([measureLyric]); one that reads one way only is measured by its
 * widths, which is all a left-to-right one ever needed.
 *
 * A right-to-left row is laid out again, up to [MAX_PADDING_PASSES] times, while a chord would have to move off the edge
 * it hangs from, past the column's right edge or into the chord before it: the padding fitted piece by piece cannot
 * tell where a layout that mixes directions draws each piece. The room missing is added as more marked padding where it
 * opens on the chord's side: before the chord's own piece, or for a chord hanging rightwards from a Latin word or a
 * number, before that run, which is drawn as a whole with its start at the right. Every chord then stands on its letter,
 * where the import finds it.
 */
internal class PrintLyricRows(
    private val options: PrintSettings,
    private val capacity: Float,
    private val measure: (String, PrintStyle) -> Float,
    private val measureLyric: PrintLyricMeasure?,
) {
    fun rows(
        line: ChordProLine.Lyrics,
        lineWidth: Float,
        lyricStyle: PrintStyle,
        chordStyle: PrintStyle,
        annotationStyle: PrintStyle,
    ): List<Row> {
        // A chord row's repeat counts and bar lines are read with its chords, so they go with them rather than with the comments.
        val visible = line.copy(chords = line.chords.filter { if (it.isAnnotation && !it.isMark) options.showComments else options.showChords })
        // Hiding a chord-only line must also remove the empty lyric row beneath it.
        if (line.chords.isNotEmpty() && visible.chords.isEmpty() && visible.text.isPrintBlank()) return emptyList()
        if (visible.chords.isEmpty()) return layOut(visible, visible, lineWidth, lyricStyle, chordStyle, annotationStyle, missingPadding = null)
        val extraPadding = mutableMapOf<Int, Int>()
        repeat(if (lyricStyle.isRightToLeft) MAX_PADDING_PASSES else 0) {
            val missingPadding = mutableMapOf<Int, Int>()
            val rows = layOut(padded(visible, lineWidth, lyricStyle, chordStyle, annotationStyle, extraPadding), visible, lineWidth, lyricStyle, chordStyle, annotationStyle, missingPadding)
            if (missingPadding.isEmpty()) return rows
            missingPadding.forEach { (index, count) -> extraPadding[index] = (extraPadding[index] ?: 0) + count }
        }
        return layOut(padded(visible, lineWidth, lyricStyle, chordStyle, annotationStyle, extraPadding), visible, lineWidth, lyricStyle, chordStyle, annotationStyle, missingPadding = null)
    }

    /**
     * [visible] padded to fit its chords. A chord wider than the column is wrapped onto rows of its own, so the lyrics
     * under it are padded to a column at most rather than to its full width.
     */
    private fun padded(
        visible: ChordProLine.Lyrics,
        lineWidth: Float,
        lyricStyle: PrintStyle,
        chordStyle: PrintStyle,
        annotationStyle: PrintStyle,
        extraPadding: Map<Int, Int>,
    ) = visible.padLyricsToFitChords(
        chordWidths = visible.chords.map { chord ->
            minOf(measure(chord.name, if (chord.isAnnotation) annotationStyle else chordStyle), lineWidth)
        },
        gap = measure(" ", lyricStyle),
        paddingWidth = measure(" ", lyricStyle),
        measureWidth = { measure(it, lyricStyle) },
        paragraphDirection = lyricStyle.direction ?: WritingDirection.LEFT_TO_RIGHT,
        marksPadding = true,
        extraPadding = extraPadding,
    )

    /**
     * The rows of [lyrics], [original] padded. Where [missingPadding] is given, the first chord of each row that has to
     * move off its edge records there the padding it lacks, by the offset of [original]'s text it goes before.
     */
    private fun layOut(
        lyrics: ChordProLine.Lyrics,
        original: ChordProLine.Lyrics,
        lineWidth: Float,
        lyricStyle: PrintStyle,
        chordStyle: PrintStyle,
        annotationStyle: PrintStyle,
        missingPadding: MutableMap<Int, Int>?,
    ): List<Row> {
        val isRightToLeft = lyricStyle.isRightToLeft
        val chordLineHeight = options.fontSize * 1.3f
        val wrappingStyle = lyrics.formatting.wrappingStyle(lyricStyle)
        val fragments = wrapPrintText(lyrics.text, lineWidth) { measure(it, wrappingStyle) }
        var start = 0
        return buildList {
            fragments.forEachIndexed { fragmentIndex, fragment ->
                val end = start + fragment.length
                val formatting = lyrics.formatting.clippedTo(start, fragment)
                val chordIndices = lyrics.chords.indices.filter { lyrics.chords[it].position >= start && (lyrics.chords[it].position < end || fragmentIndex == fragments.lastIndex) }
                val chords = chordIndices.map { lyrics.chords[it] }
                val offsets = chords.map { (it.position - start).coerceIn(0, fragment.length) }
                val layout = if (measureLyric != null && (isRightToLeft || fragment.any { isStrongRtl(it.code) })) {
                    measureLyric(fragment, lyricStyle, formatting, offsets)
                } else {
                    arithmeticLyricLayout(styledPieces(lyrics.formatting, start, fragment, lyricStyle), fragment.length, offsets, isRightToLeft, measure)
                }
                val lyricX = if (isRightToLeft) lineWidth - layout.width else 0f
                // Chords anchored at the same character are kept beside each other; if the chord run is wider than the
                // column, extra chord-only rows keep every symbol readable.
                // A row the lyric was wrapped onto at a space is marked for the import to join back to the one before, and
                // so is its chord row, which may hold nothing but an annotation that no chord row would be taken for.
                val isContinuation = fragmentIndex > 0 && !fragment.isPrintBlank() &&
                    (fragments[fragmentIndex - 1].trimEnd('\u200B').lastOrNull()?.isWhitespace() == true || fragment.firstOrNull()?.isWhitespace() == true)
                val parts = mutableListOf<Part>()
                var previousChordEdge = if (isRightToLeft) lineWidth else 0f
                var chordY = 0f
                var isMissingPaddingFound = false
                chords.forEachIndexed { index, chord ->
                    val anchor = layout.anchors[index]
                    // An annotation is an instruction rather than a chord, so it is set apart from the chords.
                    val style = if (chord.isAnnotation) annotationStyle else chordStyle
                    val chordWidth = measure(chord.name, style)
                    if (missingPadding != null && !isMissingPaddingFound && chordWidth < lineWidth) {
                        val wanted = lyricX + anchor.x - if (anchor.isRightToLeft) chordWidth else 0f
                        val overlap = wanted + chordWidth - minOf(previousChordEdge, lineWidth)
                        if (overlap > PADDING_TOLERANCE) {
                            isMissingPaddingFound = true
                            val position = original.chords[chordIndices[index]].position
                            val before = if (anchor.isRightToLeft) position else original.text.leftToRightRunStart(position)
                            missingPadding[before] = ceil(overlap / measure(" ", lyricStyle)).toInt()
                        }
                    }
                    var x = printChordLeft(lyricX + anchor.x, chordWidth, lineWidth, previousChordEdge, anchor.isRightToLeft, isRightToLeft)
                        ?: run {
                            chordY += chordLineHeight
                            if (isRightToLeft) lineWidth - chordWidth else 0f
                        }
                    val names = wrapPrintText(chord.name, lineWidth) { measure(it, style) }
                    names.forEachIndexed { nameIndex, name ->
                        if (nameIndex > 0) {
                            chordY += chordLineHeight
                            x = if (isRightToLeft) lineWidth - measure(name, style) else 0f
                        } else if (isRightToLeft && names.size > 1) {
                            x = lineWidth - measure(name, style)
                        }
                        parts += Part(name, x = x, y = chordY, style = style, isContinuation = isContinuation && parts.isEmpty())
                    }
                    val lastWidth = if (names.size == 1) chordWidth else measure(names.last(), style)
                    previousChordEdge = if (isRightToLeft) x - measure(" ", lyricStyle) else x + lastWidth + measure(" ", lyricStyle)
                }
                // A fragment of chords over nothing but padding is one row of chords, not chords over an empty lyric row;
                // a blank fragment without chords is an empty line the song asked for.
                val isChordsOnly = parts.isNotEmpty() && fragment.isPrintBlank()
                val lyricY = if (parts.isEmpty()) 0f else chordY + chordLineHeight
                if (!isChordsOnly) parts += Part(fragment, x = lyricX, y = lyricY, style = lyricStyle, formatting = formatting, isContinuation = isContinuation)
                val rowHeight = (if (isChordsOnly) chordY else lyricY) + options.fontSize * 1.45f
                if (rowHeight <= capacity) {
                    add(Row(parts, rowHeight))
                } else {
                    // A very long annotation can span a page by itself. Keep its final line with the lyrics while
                    // allowing its preceding lines to flow through the document.
                    val byLine = parts.groupBy { it.y }.entries.toList()
                    byLine.dropLast(2).forEach { (_, values) -> add(Row(values.map { it.copy(y = 0f) }, chordLineHeight)) }
                    val last = byLine.takeLast(2)
                    val top = last.first().key
                    add(Row(last.flatMap { it.value }.map { it.copy(y = it.y - top) }, options.fontSize * 2.75f))
                }
                start = end
            }
        }
    }
}

/**
 * Blank once the padding the chords add (no-break spaces, the zero-width break opportunities, and the direction marks
 * closing in a piece read against its line) is disregarded too.
 */
internal fun String.isPrintBlank() = all { it.isWhitespace() || it == ' ' || it == '​' || it == '‎' || it == '‏' || it == PADDING_MARK }

/** Where the word holding [offset] starts, or the part of it after its last right-to-left letter. */
private fun String.leftToRightRunStart(offset: Int): Int {
    var start = offset.coerceAtMost(length)
    while (start > 0 && !this[start - 1].isWhitespace() && !isStrongRtl(this[start - 1].code)) start--
    return start
}

/** How many times a right-to-left row is laid out again for the room its chords still lack. */
private const val MAX_PADDING_PASSES = 6

/** How far, in points, a chord may stand off its edge: the import reads Campfire's own chords to within 0.75 points. */
private const val PADDING_TOLERANCE = 0.5f
