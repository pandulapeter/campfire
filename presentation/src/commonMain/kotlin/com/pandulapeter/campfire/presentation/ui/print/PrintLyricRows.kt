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

/**
 * The rows of a line of lyrics with its chords, at most [capacity] tall each, laid out in its paragraph direction (its
 * lyric style's): a right-to-left row is drawn from the right edge of its column, and its chords are kept apart and
 * wrapped leftwards, an overflowing one starting the next chord line at the right edge. A row that mixes directions
 * takes its chords' places from the renderer's layout ([measureLyric]); one that reads one way only is measured by its
 * widths, which is all a left-to-right one ever needed.
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
        val isRightToLeft = lyricStyle.isRightToLeft
        // A chord wider than the column is wrapped onto rows of its own, so the lyrics under it are padded to a column at
        // most rather than to its full width.
        val lyrics = if (visible.chords.isNotEmpty()) {
            visible.padLyricsToFitChords(
                chordWidths = visible.chords.map { chord ->
                    minOf(measure(chord.name, if (chord.isAnnotation) annotationStyle else chordStyle), lineWidth)
                },
                gap = measure(" ", lyricStyle),
                paddingWidth = measure(" ", lyricStyle),
                measureWidth = { measure(it, lyricStyle) },
                paragraphDirection = lyricStyle.direction ?: WritingDirection.LEFT_TO_RIGHT,
                marksPadding = true,
            )
        } else {
            visible
        }
        val chordLineHeight = options.fontSize * 1.3f
        val wrappingStyle = lyrics.formatting.wrappingStyle(lyricStyle)
        val fragments = wrapPrintText(lyrics.text, lineWidth) { measure(it, wrappingStyle) }
        var start = 0
        return buildList {
            fragments.forEachIndexed { fragmentIndex, fragment ->
                val end = start + fragment.length
                val formatting = lyrics.formatting.clippedTo(start, fragment)
                val chords = lyrics.chords.filter { it.position >= start && (it.position < end || fragmentIndex == fragments.lastIndex) }
                val offsets = chords.map { (it.position - start).coerceIn(0, fragment.length) }
                val layout = if (measureLyric != null && (isRightToLeft || fragment.any { isStrongRtl(it.code) })) {
                    measureLyric(fragment, lyricStyle, formatting, offsets)
                } else {
                    arithmeticLyricLayout(styledPieces(lyrics.formatting, start, fragment, lyricStyle), fragment.length, offsets, isRightToLeft, measure)
                }
                val lyricX = if (isRightToLeft) lineWidth - layout.width else 0f
                // Chords anchored at the same character are kept beside each other; if the chord run is wider than the
                // column, extra chord-only rows keep every symbol readable.
                val parts = mutableListOf<Part>()
                var previousChordEdge = if (isRightToLeft) lineWidth else 0f
                var chordY = 0f
                chords.forEachIndexed { index, chord ->
                    val anchor = layout.anchors[index]
                    // An annotation is an instruction rather than a chord, so it is set apart from the chords.
                    val style = if (chord.isAnnotation) annotationStyle else chordStyle
                    val chordWidth = measure(chord.name, style)
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
                        parts += Part(name, x = x, y = chordY, style = style)
                    }
                    val lastWidth = if (names.size == 1) chordWidth else measure(names.last(), style)
                    previousChordEdge = if (isRightToLeft) x - measure(" ", lyricStyle) else x + lastWidth + measure(" ", lyricStyle)
                }
                // A fragment of chords over nothing but padding is one row of chords, not chords over an empty lyric row;
                // a blank fragment without chords is an empty line the song asked for.
                val isChordsOnly = parts.isNotEmpty() && fragment.isPrintBlank()
                val lyricY = if (parts.isEmpty()) 0f else chordY + chordLineHeight
                if (!isChordsOnly) parts += Part(fragment, x = lyricX, y = lyricY, style = lyricStyle, formatting = formatting)
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
