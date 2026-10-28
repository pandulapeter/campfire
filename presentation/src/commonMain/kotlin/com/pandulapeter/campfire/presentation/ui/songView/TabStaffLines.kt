/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songView

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import com.pandulapeter.campfire.chordpro.model.TabRowLine
import com.pandulapeter.campfire.chordpro.tabs.ChordProTabWrapper
import kotlin.math.roundToInt

/**
 * How the lines of tablature are set: engraved, as the tab staff of a tune's sheet music is, rather than typed. The
 * columns of the file's monospaced text still say where everything goes, but nothing is drawn in a monospaced face. What
 * stands on a string (a fret, a string name, the letter of a hammer-on or a dead note, a repeat count) is set in the
 * lyrics' face's bold tabular figures (a string's name in the regular weight), centred on its string and on the columns
 * it takes, in the proportions of the sheet music's tab staff ([TabEngraving]). Every other line of a run is set
 * in the lyrics' face from the column each word starts at, pushed along where the word before it ran past its columns,
 * since a proportional word is not as wide as the characters it was typed in; a line of chord names in the chords' style.
 */
internal class TabStaffLines(styles: RunStyles, private val textMeasurer: TextMeasurer) {

    private val textStyle = styles.text
    private val nameStyle = textStyle.copy(
        fontSize = if (textStyle.fontSize.isSpecified) textStyle.fontSize * TabEngraving.FRET_SIZE_PER_EM else textStyle.fontSize,
        lineHeight = TextUnit.Unspecified,
        fontFeatureSettings = TabEngraving.TABULAR_FIGURES,
    )
    private val fretStyle = nameStyle.copy(fontWeight = FontWeight.Bold)
    private val wordStyle = textStyle.copy(lineHeight = TextUnit.Unspecified)
    private val chordStyle = styles.chord.copy(lineHeight = TextUnit.Unspecified)
    private val figures = measure(FIGURE_SAMPLE.repeat(FIGURE_SAMPLE_LENGTH), fretStyle)

    private val textSize = if (textStyle.fontSize.isSpecified) {
        with(figures.layoutInput.density) { textStyle.fontSize.toPx() }
    } else {
        figures.firstBaseline / TabEngraving.FRET_SIZE_PER_EM
    }

    /** How tall a fret number stands on its baseline: the figures are lining, as tall as a capital. */
    private val figureHeight = textSize * TabEngraving.FRET_SIZE_PER_EM * TabEngraving.CAP_HEIGHT_PER_EM

    /**
     * The width of a column. A position inside the text is not rounded to whole pixels as its size is, so the figure is
     * measured from a long row of them.
     */
    val columnWidth = figures.getHorizontalPosition(FIGURE_SAMPLE_LENGTH, usePrimaryDirection = true) / FIGURE_SAMPLE_LENGTH *
        TabEngraving.COLUMN_WIDTH_PER_FIGURE

    /** The height of a line of the staff, and the gap between two systems. */
    val stringSpacing = (textSize * TabEngraving.STRING_SPACING_PER_EM).roundToInt()

    /** How far below the top of a staff line its string runs: through its middle, where the figures are centred. */
    val stringOffset = stringSpacing / 2f

    private val wordLineHeight = maxOf(measure(LINE_HEIGHT_SAMPLE, wordStyle).size.height, measure(LINE_HEIGHT_SAMPLE, chordStyle).size.height)

    fun line(line: TabRowLine) = if (line.isStaffLine) staffLine(line) else wordLine(line.text)

    private fun staffLine(line: TabRowLine): TabLine {
        val text = line.drawnText
        val nameEnd = line.stringNameEnd
        val texts = WORD.findAll(text).map { word ->
            val layout = measure(word.value, if (word.range.first < nameEnd) nameStyle else fretStyle)
            val centre = (word.range.first + word.range.last + 1) / 2f * columnWidth
            PlacedTabText(layout = layout, x = centre - layout.size.width / 2f, y = stringOffset - layout.firstBaseline + figureHeight / 2f)
        }.toList()
        return TabLine(height = stringSpacing, texts = texts, width = maxOf(text.length * columnWidth, texts.maxOfOrNull { it.x + it.layout.size.width } ?: 0f))
    }

    private fun wordLine(text: String): TabLine {
        val isChord = ChordProTabWrapper.isChordLine(text)
        var end = Float.NEGATIVE_INFINITY
        val texts = WORD.findAll(text).map { word ->
            val layout = measure(word.value, if (isChord) chordStyle else wordStyle)
            val x = maxOf(word.range.first * columnWidth, end + columnWidth)
            end = x + layout.size.width
            PlacedTabText(layout = layout, x = x, y = 0f, isChord = isChord)
        }.toList()
        return TabLine(height = wordLineHeight, texts = texts, width = end.coerceAtLeast(0f))
    }

    private fun measure(text: String, style: TextStyle) = textMeasurer.measure(AnnotatedString(text), style, softWrap = false)
}

/** One line of a row of a tab run as it is drawn: [texts] at their offsets from its top left, [height] tall, [width] wide. */
internal class TabLine(val height: Int, val texts: List<PlacedTabText>, val width: Float) {

    companion object {
        /** A line of preformatted text, drawn whole as it was laid out, in the chords' colour where it [isChord]. */
        fun of(layout: TextLayoutResult, isChord: Boolean) = TabLine(
            height = layout.size.height,
            texts = listOf(PlacedTabText(layout = layout, x = 0f, y = 0f, isChord = isChord)),
            width = layout.size.width.toFloat(),
        )
    }
}

/** A text of a [TabLine] and where it is drawn, in the chords' colour where it [isChord]. */
internal class PlacedTabText(val layout: TextLayoutResult, val x: Float, val y: Float, val isChord: Boolean = false)

private val WORD = Regex("\\S+")
private const val FIGURE_SAMPLE = "0"
private const val FIGURE_SAMPLE_LENGTH = 64
