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

/** The x of every `M x y` move of abc2svg's bar path data; what the pattern cannot read adds nothing. */
internal fun barStrokeXsOf(pathData: String): List<Float> = BAR_MOVE.findAll(pathData).mapNotNull { it.groupValues[1].toFloatOrNull() }.toList()

/** Whether [codePoint] of abc2svg's music font is music played, rather than a clef, a time or key signature, or a repeat sign. */
internal fun isMusicGlyph(codePoint: Int): Boolean = codePoint !in CLEFS_AND_TIME_SIGNATURES && codePoint !in ACCIDENTALS && codePoint !in REPEAT_SIGNS

/**
 * The bars a staff system plays, from abc2svg's bar strokes at [barXs] and the first music glyph at [firstMusicX]
 * (null: the system draws no music, a tune's title and composer, words printed after it, an empty staff): strokes
 * closer than [BAR_STROKE_GAP] units are one bar line (a double, final, or repeat bar, or one drawn on each staff of a
 * grand staff), a first bar line with no music before it (`|:`, `[|`, a `|` opening the line) closes no bar, and a
 * system of music with no bar line closing one (a tune in free time) plays one.
 */
internal fun barCountOf(barXs: List<Float>, firstMusicX: Float?): Int {
    if (firstMusicX == null) return 0
    val barLines = mutableListOf<Float>()
    barXs.sorted().forEach { x -> if (barLines.isEmpty() || x - barLines.last() >= BAR_STROKE_GAP) barLines += x }
    val opensWithBarLine = barLines.firstOrNull()?.let { it < firstMusicX } == true
    return (if (opensWithBarLine) barLines.size - 1 else barLines.size).coerceAtLeast(1)
}

/** How many lines of the song a staff system of [bars] takes to play: two bars to a line, as a lyric line is timed (`ownSecondsPerLine`). */
internal fun playedLinesOf(bars: Int) = (bars + 1) / 2

/** The lines the piece at [slot] of a run engraved as systems playing [systemLines] plays (see `SongNotationBlock`'s slots). */
internal fun notationSlotLines(systemLines: List<Int>, slot: Int, isLastSlot: Boolean) = systemLines.inSlot(slot, isLastSlot).sum()

/** The systems of a run that the piece at [slot] draws: that one, or every one from there on where it [isLastSlot]. */
internal fun <T> List<T>.inSlot(slot: Int, isLastSlot: Boolean) = if (isLastSlot) drop(slot) else listOfNotNull(getOrNull(slot))

/** abc2svg's thin-thick and repeat bars sit 3–6 units apart (`::` 6); two bars of music are never closer than 8. */
private const val BAR_STROKE_GAP = 8f
private val BAR_MOVE = Regex("M\\s*(-?[0-9]*\\.?[0-9]+)")
private val CLEFS_AND_TIME_SIGNATURES = 0xE050..0xE09F
private val ACCIDENTALS = 0xE260..0xE26F
private val REPEAT_SIGNS = 0xE040..0xE04F
