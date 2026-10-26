/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.chords

import androidx.compose.runtime.Immutable
import com.pandulapeter.campfire.chordpro.voicings.ChordVoicings
import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.chordpro.model.ChordTuning
import com.pandulapeter.campfire.chordpro.model.ChordVoicing

/**
 * What a chord diagram draws, in the diagram's own units rather than in pixels: strings, frets and keys counted from
 * the top left. It is pure so that it can be tested, and so that the PDF export draws from it as well as the screen.
 */
@Immutable
internal sealed interface ChordDiagramGeometry {

    /**
     * A fretted shape: [strings] vertical lines, [fretCount] frets under the nut (a heavy line where [baseFret] is 1,
     * and otherwise a thin one with the base fret's number beside it).
     *
     * @property markers Above the nut, per string: muted, open, open on the chord's root, or nothing for a stopped
     *   string. An open root is marked like a stopped one, since the root is what a hand finds the chord by.
     * @property dots The stopped strings no barre holds, each on its [Dot.row], 0 being the window's first fret.
     * @property barres The fingers laid across several strings.
     * @property capoRow The row a capo lies across every string on, null where none is drawn: no capo, or a shape played
     *   so far above it that its window leaves the capo out. Its strings ring at the capo where [markers] say open.
     */
    data class Fretted(
        val strings: Int,
        val baseFret: Int,
        val fretCount: Int,
        val markers: List<Marker?>,
        val dots: List<Dot>,
        val barres: List<Barre>,
        val capoRow: Int? = null,
    ) : ChordDiagramGeometry {

        enum class Marker { MUTED, OPEN, OPEN_ROOT }

        data class Dot(val string: Int, val row: Int, val finger: Int?, val isRoot: Boolean)

        data class Barre(val row: Int, val fromString: Int, val toString: Int, val finger: Int?)
    }

    /**
     * A keyboard of [octaves] octaves from a C.
     *
     * @property keys The pressed keys, in semitones above that C.
     * @property roots Those of [keys] that are the chord's root.
     * @property bass The bass of a slash chord, ringed, or null.
     */
    data class Keyboard(
        val octaves: Int,
        val keys: Set<Int>,
        val roots: Set<Int>,
        val bass: Int?,
    ) : ChordDiagramGeometry
}

/**
 * The geometry of [shape] in [tuning], its root dots and keys marked by the chord's [root] pitch class, read against
 * the tuning's own [ChordTuning.strings], since the shape sounds the chord the page names on them.
 *
 * Under a [capo] the shape's frets are counted from the capo, as a shape is fingered, and the chord [root] names is the
 * one that sounds: the shape is drawn where it is played, its frets numbered from the nut, with the capo across every
 * string on the window's first row where the shape is fingered right above it (a G shape at the second fret is the frets
 * 2 to 5, the capo on 2), and from the shape's own lowest fret, the capo below the window, where it is played higher.
 *
 * A fretted shape is drawn in four frets, or as many as it spans, from the nut where it fits there and otherwise from
 * its lowest stopped fret ([ChordVoicings.baseFret], which a written definition agrees with). A barre is a finger
 * the fingering names on several strings at one fret, or, for a shape that names no fingers, the lowest stopped fret
 * where the shape takes more than four fingers without one, or where the index finger lies across the thin strings
 * under a higher fretted one, with no open or muted string under it.
 *
 * Whatever the shape, the diagram is bounded: at most [MAX_FRETS] frets and [MAX_OCTAVES] octaves, a dot or a key past
 * them left out, since every source of a shape meets here and a broken file's is better drawn in part than not at all.
 */
internal fun chordDiagramGeometryOf(shape: ChordVoicing, tuning: ChordTuning, root: Int, capo: Int = 0): ChordDiagramGeometry =
    if (capo <= 0 || shape !is ChordVoicing.Fretted) {
        nutGeometryOf(shape, tuning, root)
    } else {
        // The shape is read as if the capo were the nut, the root moved down with it, and the window then moved up the neck.
        (nutGeometryOf(shape, tuning, (root - capo).mod(12)) as ChordDiagramGeometry.Fretted).aboveCapo(capo)
    }

/**
 * [this], a shape's geometry read from the nut, played above a capo on [capo]: from the capo's own row where the shape
 * fits from the nut, every row one further down, else at the shape's own lowest fret moved up by the capo.
 */
private fun ChordDiagramGeometry.Fretted.aboveCapo(capo: Int): ChordDiagramGeometry.Fretted = if (baseFret == 1) {
    val dots = dots.map { it.copy(row = it.row + 1) }.filter { it.row < MAX_FRETS }
    val barres = barres.map { it.copy(row = it.row + 1) }.filter { it.row < MAX_FRETS }
    val lowestRow = (dots.map { it.row } + barres.map { it.row }).maxOrNull() ?: 0
    copy(baseFret = capo, fretCount = maxOf(MIN_FRETS, lowestRow + 1), dots = dots, barres = barres, capoRow = 0)
} else {
    copy(baseFret = baseFret + capo)
}

private fun nutGeometryOf(shape: ChordVoicing, tuning: ChordTuning, root: Int): ChordDiagramGeometry = when (shape) {
    is ChordVoicing.Keys -> {
        val octaves = ((listOfNotNull(shape.bass) + shape.notes).maxOrNull() ?: 0).let { it / 12 + 1 }.coerceIn(MIN_OCTAVES, MAX_OCTAVES)
        val range = 0 until octaves * 12
        ChordDiagramGeometry.Keyboard(
            octaves = octaves,
            keys = shape.notes.filter { it in range }.toSet(),
            roots = shape.notes.filter { it in range && it % 12 == root }.toSet(),
            bass = shape.bass?.takeIf { it in range },
        )
    }
    is ChordVoicing.Fretted -> {
        val frets = shape.frets
        val baseFret = ChordVoicings.baseFret(frets)
        val stopped = frets.filterNotNull().filter { it > 0 }
        val fretCount = maxOf(MIN_FRETS, (stopped.maxOrNull() ?: 0) - baseFret + 1).coerceAtMost(MAX_FRETS)
        val barres = barresOf(frets, shape.fingers).filter { it.row in 0 until fretCount }
        val heldByBarre = barres.flatMap { barre -> (barre.fromString..barre.toString).filter { frets[it] == barre.row + baseFret } }.toSet()
        val strings = tuning.strings
        ChordDiagramGeometry.Fretted(
            strings = frets.size,
            baseFret = baseFret,
            fretCount = fretCount,
            markers = frets.mapIndexed { string, fret ->
                when (fret) {
                    null -> ChordDiagramGeometry.Fretted.Marker.MUTED
                    0 -> if (isRootAt(strings, string, 0, root)) ChordDiagramGeometry.Fretted.Marker.OPEN_ROOT else ChordDiagramGeometry.Fretted.Marker.OPEN
                    else -> null
                }
            },
            dots = frets.indices.filter { (frets[it] ?: 0) > 0 && (it !in heldByBarre || isRootAt(strings, it, frets[it]!!, root)) }.map { string ->
                ChordDiagramGeometry.Fretted.Dot(
                    string = string,
                    row = frets[string]!! - baseFret,
                    finger = shape.fingers?.getOrNull(string)?.takeIf { it > 0 },
                    isRoot = isRootAt(strings, string, frets[string]!!, root),
                )
            }.filter { it.row in 0 until fretCount },
            barres = barres,
        )
    }
}

private fun isRootAt(strings: List<Int>, string: Int, fret: Int, root: Int) =
    strings.getOrNull(string)?.let { (it + fret).mod(12) == root } == true

/** The barres of [frets], each on its row of the window the diagram draws. */
private fun barresOf(frets: List<Int?>, fingers: List<Int>?): List<ChordDiagramGeometry.Fretted.Barre> {
    val baseFret = ChordVoicings.baseFret(frets)
    if (fingers != null) {
        return fingers.indices.filter { fingers[it] > 0 && (frets[it] ?: 0) > 0 }
            .groupBy { fingers[it] to frets[it]!! }
            .filterValues { it.size >= 2 }
            .map { (finger, strings) ->
                ChordDiagramGeometry.Fretted.Barre(row = finger.second - baseFret, fromString = strings.min(), toString = strings.max(), finger = finger.first)
            }
    }
    val stopped = frets.indices.filter { (frets[it] ?: 0) > 0 }
    if (stopped.isEmpty()) return emptyList()
    val lowest = stopped.minOf { frets[it]!! }
    val atLowest = stopped.filter { frets[it] == lowest }
    if (atLowest.size < 2) return emptyList()
    val from = atLowest.first()
    val to = atLowest.last()
    // The index finger laid flat across the thin strings under a higher fretted one, as in a small F; anything else that
    // four fingers can hold one string each is drawn as dots, as the table fingers an Em or an A.
    val isShortBarre = to - from + 1 == atLowest.size && to == frets.lastIndex && from > 0 && (frets[from - 1] ?: 0) > lowest
    if (stopped.size <= MAX_FINGERS && !isShortBarre) return emptyList()
    if ((from..to).any { (frets[it] ?: 0) < lowest }) return emptyList()
    return listOf(ChordDiagramGeometry.Fretted.Barre(row = lowest - baseFret, fromString = from, toString = to, finger = null))
}

/** The frame a diagram of [instrument] is drawn in where the chord has no shape on it: its strings and frets, or its keys. */
internal fun emptyChordDiagramGeometryOf(instrument: ChordInstrument): ChordDiagramGeometry = if (instrument.isFretted) {
    ChordDiagramGeometry.Fretted(
        strings = instrument.tuning.size,
        baseFret = 1,
        fretCount = MIN_FRETS,
        markers = List(instrument.tuning.size) { null },
        dots = emptyList(),
        barres = emptyList(),
    )
} else {
    ChordDiagramGeometry.Keyboard(octaves = MIN_OCTAVES, keys = emptySet(), roots = emptySet(), bass = null)
}

private const val MIN_FRETS = 4
private const val MAX_FRETS = 24
private const val MAX_FINGERS = 4
private const val MIN_OCTAVES = 2
private const val MAX_OCTAVES = 4
