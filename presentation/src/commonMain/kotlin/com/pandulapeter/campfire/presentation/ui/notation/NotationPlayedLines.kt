/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.notation

/** Where one stroke of a bar line starts: its [x], and the [y] of the staff it is drawn down from. */
internal data class NotationBarStroke(val x: Float, val y: Float)

/** Every `M x y` move of abc2svg's bar path data; what the pattern cannot read adds nothing. */
internal fun barStrokesOf(pathData: String): List<NotationBarStroke> = BAR_MOVE.findAll(pathData).mapNotNull { match ->
    NotationBarStroke(x = match.groupValues[1].toFloatOrNull() ?: return@mapNotNull null, y = match.groupValues[2].toFloatOrNull() ?: return@mapNotNull null)
}.toList()

/**
 * Whether [codePoint] of abc2svg's music font is music played, rather than the brace joining a grand staff, a clef, a
 * time or key signature, or a repeat sign.
 */
internal fun isMusicGlyph(codePoint: Int): Boolean =
    codePoint != BRACE && codePoint !in CLEFS_AND_TIME_SIGNATURES && codePoint !in ACCIDENTALS && codePoint !in REPEAT_SIGNS

/**
 * The bars a staff system plays, from abc2svg's [barStrokes] and the first music glyph at [firstMusicX] (null: the
 * system draws no music, a tune's title and composer, words printed after it, an empty staff). Each staff is counted
 * on its own and the most bars any plays are the system's: the voices of a score exported with uneven bars draw their
 * bar lines at different places, and counting them together would make each of those another bar. On a staff, strokes
 * closer than [BAR_STROKE_GAP] units are one bar line (a double, final, or repeat bar), a first bar line with no music
 * before it (`|:`, `[|`, a `|` opening the line) closes no bar, and a system of music with no bar line closing one (a
 * tune in free time) plays one.
 */
internal fun barCountOf(barStrokes: List<NotationBarStroke>, firstMusicX: Float?): Int {
    if (firstMusicX == null) return 0
    val staffBars = barStrokes.groupBy { it.y }.values.map { strokes ->
        val barLines = mutableListOf<Float>()
        strokes.map { it.x }.sorted().forEach { x -> if (barLines.isEmpty() || x - barLines.last() >= BAR_STROKE_GAP) barLines += x }
        val opensWithBarLine = barLines.firstOrNull()?.let { it < firstMusicX } == true
        if (opensWithBarLine) barLines.size - 1 else barLines.size
    }
    return (staffBars.maxOrNull() ?: 0).coerceAtLeast(1)
}

/** How many lines of the song a staff system of [bars] takes to play: two bars to a line, as a lyric line is timed (`ownSecondsPerLine`). */
internal fun playedLinesOf(bars: Int) = (bars + 1) / 2

/** The lines the piece at [slot] of a run engraved as systems playing [systemLines] plays (see `SongNotationBlock`'s slots). */
internal fun notationSlotLines(systemLines: List<Int>, slot: Int, isLastSlot: Boolean) = systemLines.inSlot(slot, isLastSlot).sum()

/** The systems of a run that the piece at [slot] draws: that one, or every one from there on where it [isLastSlot]. */
internal fun <T> List<T>.inSlot(slot: Int, isLastSlot: Boolean) = if (isLastSlot) drop(slot) else listOfNotNull(getOrNull(slot))

/** abc2svg's thin-thick and repeat bars sit 3–6 units apart (`::` 6); two bars of music are never closer than 8. */
private const val BAR_STROKE_GAP = 8f
private val BAR_MOVE = Regex("M\\s*(-?[0-9]*\\.?[0-9]+)[\\s,]*(-?[0-9]*\\.?[0-9]+)")
private const val BRACE = 0xE000
private val CLEFS_AND_TIME_SIGNATURES = 0xE050..0xE09F
private val ACCIDENTALS = 0xE260..0xE26F
private val REPEAT_SIGNS = 0xE040..0xE04F
