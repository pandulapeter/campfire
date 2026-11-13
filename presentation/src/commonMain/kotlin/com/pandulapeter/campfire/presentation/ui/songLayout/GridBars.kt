/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.songLayout

import com.pandulapeter.campfire.chordpro.model.GridToken

/**
 * One `{start_of_grid}` line read as a chord chart: the label in its left [margin] (`Intro`), its [bars], and what is
 * written after the last of them ([trailing], a repeat count such as `x2`).
 */
internal data class GridBarLine(
    val margin: String?,
    val bars: List<GridBar>,
    val trailing: String?,
)

/**
 * One bar of a grid line, drawn as a box: the [opening] and [closing] edges its bar lines make of it, the [volta] its
 * opening bar line numbers it with (`1.`), and its [segments], which together last [beats].
 */
internal data class GridBar(
    val opening: GridEdge,
    val closing: GridEdge,
    val volta: String?,
    val segments: List<GridSegment>,
    val beats: Int,
)

/**
 * What the bar lines of a grid make of a box's side: [PLAIN] for `|`, [HEAVY] for `||` and the closing `|.`, and [REPEAT]
 * for a heavy one with the two dots of `|:` or `:|` inside it.
 */
internal enum class GridEdge { PLAIN, HEAVY, REPEAT }

/** A part of a [GridBar] as wide as its [beats]. */
internal sealed interface GridSegment {
    val beats: Int

    /** One chord or several (`C~G`) played on the cell the segment starts on and held for its beats. */
    data class Chord(val names: List<String>, override val beats: Int) : GridSegment

    /** Beats in which the chord before is still held: an empty bar, or the beats before a bar's first chord. */
    data class Hold(override val beats: Int) : GridSegment

    /** `%` or `%%`: the last one or two [bars] played again, which a file writes on purpose, so the sign is kept. */
    data class Repeat(val bars: Int, override val beats: Int) : GridSegment
}

/**
 * The lines of one run of a grid as chord chart lines. A cell is a beat: a chord starts a segment, a `.` or a `/`
 * lengthens the one before it, or holds the previous bar's chord where the bar starts with one. A bar that is empty, or
 * holds nothing but a repeat sign, lasts the run's most common bar length ([commonBarLength]), and the bar after a `%%`
 * that is empty has no segment, since the sign standing over both bars says what it holds.
 *
 * A line that leaves out its outer bar lines (`G | D | Em | C`) still has those cells as its first and last bars.
 */
internal fun gridBarLinesOf(run: List<List<GridToken>>): List<GridBarLine> {
    val rawLines = run.map { it.toRawLine() }
    val commonLength = commonBarLength(rawLines.flatMap { line -> line.bars.filter { it.isTimed }.map { it.cells.size } })
    return rawLines.map { line ->
        GridBarLine(
            margin = line.margin,
            bars = line.bars.mapIndexed { index, bar ->
                val isCoveredByRepeat = bar.cells.isEmpty() && (line.bars.getOrNull(index - 1)?.cells?.singleOrNull() as? GridToken.Repeat)?.text == "%%"
                val segments = when {
                    isCoveredByRepeat -> emptyList()
                    bar.cells.isEmpty() -> listOf(GridSegment.Hold(commonLength))
                    bar.isTimed -> bar.cells.segments()
                    else -> listOf(GridSegment.Repeat(bars = (bar.cells.single() as GridToken.Repeat).text.length, beats = commonLength))
                }
                GridBar(
                    opening = bar.opening,
                    closing = bar.closing,
                    volta = bar.volta,
                    segments = segments,
                    beats = if (bar.isTimed) bar.cells.size else commonLength,
                )
            },
            trailing = line.trailing,
        )
    }
}

/** The bar length most bars of a run have, the longer of two as common, or four beats for a run with no bar to count. */
internal fun commonBarLength(lengths: List<Int>): Int =
    lengths.groupingBy { it }.eachCount().entries.maxWithOrNull(compareBy({ it.value }, { it.key }))?.key ?: DEFAULT_BAR_LENGTH

private fun List<GridToken>.segments(): List<GridSegment> = buildList {
    this@segments.forEach { cell ->
        when (cell) {
            is GridToken.Chord -> add(GridSegment.Chord(names = cell.name.split(CHORD_SEPARATOR), beats = 1))
            is GridToken.Repeat -> add(GridSegment.Repeat(bars = cell.text.length, beats = 1))
            else -> when (val last = lastOrNull()) {
                null -> add(GridSegment.Hold(1))
                is GridSegment.Chord -> set(lastIndex, last.copy(beats = last.beats + 1))
                is GridSegment.Hold -> set(lastIndex, last.copy(beats = last.beats + 1))
                is GridSegment.Repeat -> set(lastIndex, last.copy(beats = last.beats + 1))
            }
        }
    }
}

private class RawBar(
    val opening: GridEdge,
    val volta: String?,
    val cells: List<GridToken>,
    val closing: GridEdge,
) {
    /** Whether the bar's cells say how long it is, which an empty one and one holding only a repeat sign do not. */
    val isTimed get() = cells.isNotEmpty() && !(cells.size == 1 && cells.single() is GridToken.Repeat)
}

private class RawLine(val margin: String?, val bars: List<RawBar>, val trailing: String?)

private fun List<GridToken>.toRawLine(): RawLine {
    val isLabel = { token: GridToken -> token is GridToken.Text && token.text != CHORD_POSITION }
    val firstCell = indexOfFirst { !isLabel(it) }.let { if (it < 0) size else it }
    val lastCell = indexOfLast { !isLabel(it) }
    val bars = mutableListOf<RawBar>()
    var opening = GridEdge.PLAIN
    var volta: String? = null
    var cells = mutableListOf<GridToken>()
    var isOpen = false
    for (index in firstCell..lastCell) {
        val token = this[index]
        if (token is GridToken.Bar) {
            val core = token.text.trimEnd('>').trimEnd { it.isDigit() }
            if (isOpen) bars += RawBar(opening, volta, cells, closing = closingEdgeOf(core))
            opening = if (core.endsWith(":")) GridEdge.REPEAT else GridEdge.PLAIN
            volta = token.text.filter { it.isDigit() }.takeIf { it.isNotEmpty() }?.let { "$it." }
            cells = mutableListOf()
            isOpen = true
        } else {
            cells += token
            isOpen = true
        }
    }
    // A line's last bar line opens no bar: only cells written after it (a line without its closing bar line) make one.
    if (cells.isNotEmpty()) bars += RawBar(opening, volta, cells, closing = GridEdge.PLAIN)
    return RawLine(
        margin = take(firstCell).labelText(),
        bars = bars,
        trailing = if (lastCell < 0) null else drop(lastCell + 1).labelText(),
    )
}

private fun closingEdgeOf(core: String) = when {
    core.startsWith(":") -> GridEdge.REPEAT
    core == "||" || core == "|." -> GridEdge.HEAVY
    else -> GridEdge.PLAIN
}

private fun List<GridToken>.labelText() = filterIsInstance<GridToken.Text>().joinToString(" ") { it.text }.takeIf { it.isNotEmpty() }

private const val CHORD_SEPARATOR = "~"
private const val CHORD_POSITION = "/"
private const val DEFAULT_BAR_LENGTH = 4
