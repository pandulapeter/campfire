/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import com.pandulapeter.campfire.chordpro.model.GridToken

/**
 * One piece of an aligned grid bar: a [token] drawn as [text], which carries the spaces that keep the columns after it
 * in place, or only spaces where [token] is null and the line has nothing in a column another line of the run fills.
 */
internal data class GridCell(
    val token: GridToken?,
    val text: String,
)

/**
 * The tokens of a grid line cut into bars, each ending on the bar line that closes it, so that a wrapped line never
 * starts with a stray bar line. The line that opens the first bar stays with it, and so does the margin label in front
 * of that, and whatever follows the last bar line (a repeat count, a comment) is a piece of its own.
 */
internal fun List<GridToken>.bars(): List<List<GridToken>> {
    val bars = mutableListOf<List<GridToken>>()
    var bar = mutableListOf<GridToken>()
    val firstBarIndex = indexOfFirst { it is GridToken.Bar }
    var hasCell = false
    forEachIndexed { index, token ->
        bar += token
        if (token !is GridToken.Bar && index > firstBarIndex) hasCell = true
        if (token is GridToken.Bar && hasCell) {
            bars += bar
            bar = mutableListOf()
            hasCell = false
        }
    }
    if (bar.isNotEmpty()) bars += bar
    return bars
}

/**
 * The lines of one run of a grid, laid out the way a chord chart is written in a monospaced font: every line cut into
 * its [bars], and every bar padded with spaces so that the bar lines and the cells of the lines under each other line
 * up, bar by bar and cell by cell, whatever the width of the chord in each. A bar's text ends with the space that
 * separates it from the next one, except for the last bar of a line, which ends where its last token does.
 *
 * A bar is read as its margin (the label in front of a line's first bar line), the bar line that opens it, its cells
 * and the bar line that closes it, and each of these is aligned on its own: a bar with fewer cells than the same bar
 * of another line is padded before its closing bar line, so that one still lines up with the others. [textOf] is what
 * a token is drawn as, which only has to agree with itself about the number of characters.
 *
 * Whatever follows a line's last bar line (a repeat count, a note) is drawn after it as written rather than aligned: it is
 * in the right margin of the chart, where there is nothing to line up with, and aligning it by its position would widen
 * the bar of a longer line that happens to stand above it.
 */
internal fun List<List<GridToken>>.alignedGridBars(textOf: (GridToken) -> String): List<List<List<GridCell>>> {
    val pieces = map { it.bars() }
    val trailingPieces = pieces.map { line -> line.lastOrNull()?.takeIf { line.size > 1 && it.none { token -> token is GridToken.Bar } } }
    val lines = pieces.mapIndexed { lineIndex, line ->
        (if (trailingPieces[lineIndex] == null) line else line.dropLast(1)).map { it.toBarParts() }
    }
    val barCount = lines.maxOfOrNull { it.size } ?: 0
    val marginWidths = IntArray(barCount)
    val openingWidths = IntArray(barCount)
    val cellWidths = Array(barCount) { mutableListOf<Int>() }
    val closingWidths = IntArray(barCount)
    lines.forEach { bars ->
        bars.forEachIndexed { index, bar ->
            marginWidths[index] = maxOf(marginWidths[index], bar.margin.textWidth(textOf))
            openingWidths[index] = maxOf(openingWidths[index], bar.opening?.let(textOf)?.columns ?: 0)
            bar.cells.forEachIndexed { cellIndex, cell ->
                val width = textOf(cell).columns
                if (cellIndex < cellWidths[index].size) {
                    cellWidths[index][cellIndex] = maxOf(cellWidths[index][cellIndex], width)
                } else {
                    cellWidths[index] += width
                }
            }
            closingWidths[index] = maxOf(closingWidths[index], bar.closing?.let(textOf)?.columns ?: 0)
        }
    }
    return lines.mapIndexed { lineIndex, bars ->
        bars.mapIndexed { index, bar ->
            buildList {
                if (marginWidths[index] > 0) {
                    bar.margin.forEach { add(GridCell(token = it, text = textOf(it) + " ")) }
                    val marginPadding = marginWidths[index] - bar.margin.textWidth(textOf) + if (bar.margin.isEmpty()) 1 else 0
                    if (marginPadding > 0) add(GridCell(token = null, text = " ".repeat(marginPadding)))
                }
                addAligned(bar.opening, openingWidths[index], textOf)
                cellWidths[index].forEachIndexed { cellIndex, width -> addAligned(bar.cells.getOrNull(cellIndex), width, textOf) }
                addAligned(bar.closing, closingWidths[index], textOf, isAlignedToTheEnd = true)
            }
        }.plus(listOfNotNull(trailingPieces[lineIndex]?.map { GridCell(token = it, text = textOf(it) + " ") })).trimmedAtTheEnd()
    }
}

/**
 * [token] in a column [width] characters wide and the space after it, or only spaces where it is null. A closing bar line
 * is [isAlignedToTheEnd], so that the stroke of a `:|` stands under the `|` of the lines around it, the way an opening
 * `|:` starts where their `|` does.
 */
private fun MutableList<GridCell>.addAligned(
    token: GridToken?,
    width: Int,
    textOf: (GridToken) -> String,
    isAlignedToTheEnd: Boolean = false,
) {
    if (width > 0) {
        val text = token?.let(textOf) ?: ""
        val padding = " ".repeat((width - text.columns).coerceAtLeast(0))
        add(GridCell(token = token, text = (if (isAlignedToTheEnd) padding + text else text + padding) + " "))
    }
}

/** A bar cut into its margin, its opening bar line, its cells and its closing bar line, see [alignedGridBars]. */
private class BarParts(
    val margin: List<GridToken>,
    val opening: GridToken?,
    val cells: List<GridToken>,
    val closing: GridToken?,
)

private fun List<GridToken>.toBarParts(): BarParts {
    val closingIndex = lastIndex.takeIf { it > 0 && last() is GridToken.Bar }
    val openingIndex = indexOfFirst { it is GridToken.Bar }.takeIf { it >= 0 && it != closingIndex }
    return BarParts(
        margin = if (openingIndex == null) emptyList() else subList(0, openingIndex),
        opening = openingIndex?.let(::get),
        cells = subList(openingIndex?.plus(1) ?: 0, closingIndex ?: size),
        closing = closingIndex?.let(::get),
    )
}

private fun List<GridToken>.textWidth(textOf: (GridToken) -> String) = if (isEmpty()) 0 else sumOf { textOf(it).columns } + size - 1

/**
 * The number of columns the text takes up in a monospaced font: its UTF-16 length less the combining marks, which draw on
 * the character before them, so that a label typed with a decomposed accent lines up with one typed with a composed one.
 * A surrogate pair still counts as two, since what lies outside the Basic Multilingual Plane in a chart is an emoji, which
 * a monospaced face draws from a fallback font about two columns wide.
 */
private val String.columns get() = length - count { it.category == CharCategory.NON_SPACING_MARK || it.category == CharCategory.ENCLOSING_MARK }

/** The bars of a line with the padding after its last token taken off, which would only push a wrap early. */
private fun List<List<GridCell>>.trimmedAtTheEnd(): List<List<GridCell>> {
    val bars = map { it.toMutableList() }.toMutableList()
    while (bars.isNotEmpty()) {
        val bar = bars.last()
        while (bar.isNotEmpty() && bar.last().text.isBlank()) bar.removeAt(bar.lastIndex)
        if (bar.isEmpty()) {
            bars.removeAt(bars.lastIndex)
            continue
        }
        bar[bar.lastIndex] = bar.last().copy(text = bar.last().text.trimEnd())
        break
    }
    return bars
}
