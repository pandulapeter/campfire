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

import com.pandulapeter.campfire.chordpro.model.TabRowLine
import com.pandulapeter.campfire.chordpro.tabs.ChordProTabWrapper
import com.pandulapeter.campfire.presentation.ui.songView.TabEngraving
import com.pandulapeter.campfire.presentation.ui.songView.drawnText
import com.pandulapeter.campfire.presentation.ui.songView.stringNameEnd
import com.pandulapeter.campfire.presentation.ui.songView.tabStaffGeometryOf

/**
 * Tablature printed as the viewer engraves it (`TabStaffLines`, in the proportions of [TabEngraving]): frets in the text
 * face's bold tabular figures centred on their strings, string names in the regular weight, the strings and bar lines
 * ruled, other lines in the text face from the column each word starts at (chord names in the chords' weight), a system
 * a row. None of it is selectable: each line reaches the text layer as the file has it instead, invisible, in the
 * monospaced face at the size whose characters are as wide as the columns, so that a selection covers what it reads and
 * the importer reads the tab back character for character, in the regular weight a line of chords is read back as.
 */
internal class PrintTabEngraver(
    private val fontSize: Float,
    private val measure: (text: String, style: PrintStyle) -> Float,
) {

    private val nameStyle = PrintStyle(size = fontSize * TabEngraving.FRET_SIZE_PER_EM, tabular = true)
    private val fretStyle = nameStyle.copy(bold = true)
    private val wordStyle = PrintStyle(size = fontSize)
    private val chordStyle = wordStyle.copy(bold = true)
    private val columnWidth = measure(FIGURE_SAMPLE, fretStyle) / FIGURE_SAMPLE.length * TabEngraving.COLUMN_WIDTH_PER_FIGURE
    private val textStyle = PrintStyle(
        size = fontSize * columnWidth / (measure(MONOSPACE_SAMPLE, PrintStyle(size = fontSize, monospace = true)) / MONOSPACE_SAMPLE.length),
        monospace = true,
    )
    private val stringSpacing = fontSize * TabEngraving.STRING_SPACING_PER_EM
    private val wordLineHeight = fontSize * WORD_LINE_HEIGHT_PER_EM
    private val strokeWidth = columnWidth * TabEngraving.STROKE_WIDTH_PER_COLUMN

    /** The rows of a run of tablature, wrapped into systems of [lineWidth], a string spacing between two of them. */
    fun rows(run: List<String>, lineWidth: Float): List<Row> =
        ChordProTabWrapper.wrapLines(run, (lineWidth / columnWidth).toInt().coerceAtLeast(1)).flatMapIndexed { index, system ->
            listOfNotNull(Row(emptyList(), stringSpacing).takeIf { index > 0 }, systemRow(system))
        }

    /** [part], drawn in another style, as it reaches the text layer: in the regular weight, and only there. */
    fun selectable(part: Part) = part.copy(style = part.style.copy(bold = false), isSelectable = true, isDrawn = false)

    private fun systemRow(system: List<TabRowLine>): Row {
        val tops = system.runningFold(0f) { top, line -> top + if (line.isStaffLine) stringSpacing else wordLineHeight }
        val parts = system.flatMapIndexed { index, line ->
            val top = tops[index]
            // The invisible line shares its baseline with what is drawn of it: a string's, or a line of words'.
            val baseline = if (line.isStaffLine) top + (stringSpacing + textStyle.size * MONOSPACE_CAP_HEIGHT_PER_EM) / 2 else top + fontSize * TabEngraving.ASCENT_PER_EM
            val drawn = if (line.isStaffLine) staffParts(line, top) else wordParts(line.text, top)
            drawn + Part(line.text, y = baseline - textStyle.size * MONOSPACE_ASCENT_PER_EM, style = textStyle, isDrawn = false)
        }
        return Row(parts = parts, height = tops.last(), rules = staffRules(system, tops))
    }

    private fun staffParts(line: TabRowLine, top: Float): List<Part> {
        val nameEnd = line.stringNameEnd
        val stringY = top + stringSpacing / 2
        return WORD.findAll(line.drawnText).map { word ->
            val style = if (word.range.first < nameEnd) nameStyle else fretStyle
            val centre = (word.range.first + word.range.last + 1) / 2f * columnWidth
            val y = stringY + style.size * (TabEngraving.CAP_HEIGHT_PER_EM / 2 - TabEngraving.ASCENT_PER_EM)
            Part(word.value, x = centre - measure(word.value, style) / 2, y = y, style = style, isSelectable = false)
        }.toList()
    }

    private fun wordParts(text: String, top: Float): List<Part> {
        val style = if (ChordProTabWrapper.isChordLine(text)) chordStyle else wordStyle
        var end = Float.NEGATIVE_INFINITY
        return WORD.findAll(text).map { word ->
            val x = maxOf(word.range.first * columnWidth, end + columnWidth * TabEngraving.MIN_WORD_GAP_PER_COLUMN)
            end = x + measure(word.value, style)
            Part(word.value, x = x, y = top, style = style, isSelectable = false)
        }.toList()
    }

    /**
     * The strokes of the staff of [system], whose lines start at [tops], as the viewer draws them: a bar line on a string
     * with none above or under it stops short of the lines next to it, and one that joins strings reaches half a stroke
     * past the outer ones, which closes the corner where a string meets it.
     */
    private fun staffRules(system: List<TabRowLine>, tops: List<Float>): List<PrintRule> {
        val geometry = tabStaffGeometryOf(system)
        val strings = geometry.strings.map { string ->
            val y = tops[string.line] + stringSpacing / 2
            PrintRule(x = string.from * columnWidth, y = y - strokeWidth / 2, width = (string.to - string.from) * columnWidth, height = strokeWidth, gray = STAFF_GRAY)
        }
        val bars = geometry.bars.map { bar ->
            val (from, to) = if (bar.fromLine == bar.toLine) {
                val inset = (tops[bar.fromLine + 1] - tops[bar.fromLine]) * TabEngraving.LONE_BAR_INSET
                tops[bar.fromLine] + inset to tops[bar.fromLine + 1] - inset
            } else {
                tops[bar.fromLine] + (stringSpacing - strokeWidth) / 2 to tops[bar.toLine] + (stringSpacing + strokeWidth) / 2
            }
            val x = (bar.column + HALF_COLUMN) * columnWidth
            PrintRule(x = x - strokeWidth / 2, y = from, width = strokeWidth, height = to - from, gray = STAFF_GRAY)
        }
        return strings + bars
    }
}

private val WORD = Regex("\\S+")
private val FIGURE_SAMPLE = "0".repeat(64)
private val MONOSPACE_SAMPLE = "M".repeat(64)
private const val WORD_LINE_HEIGHT_PER_EM = 1.45f // A lyric row's, as the other rows of the page have it.
private const val MONOSPACE_ASCENT_PER_EM = 1.02f // JetBrains Mono's, which the text layer's characters are set in.
private const val MONOSPACE_CAP_HEIGHT_PER_EM = 0.73f
private const val HALF_COLUMN = 0.5f

/** The viewer draws a staff in the surface's variant colour, a little lighter than its text; this is that on paper. */
private const val STAFF_GRAY = 90
