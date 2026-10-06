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
import com.pandulapeter.campfire.chordpro.ChordVoicings
import com.pandulapeter.campfire.chordpro.model.ChordInstrument
import com.pandulapeter.campfire.chordpro.model.ChordVoicing

/**
 * What a chord diagram draws, in the diagram's own units rather than in pixels: strings, frets and keys counted from
 * the top left. It is pure so that it can be tested, and so that a PDF could draw from it as well as the screen.
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
     */
    data class Fretted(
        val strings: Int,
        val baseFret: Int,
        val fretCount: Int,
        val markers: List<Marker?>,
        val dots: List<Dot>,
        val barres: List<Barre>,
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
 * The geometry of [shape] on [instrument], its root dots and keys marked by the chord's [root] pitch class.
 *
 * A fretted shape is drawn in four frets, or as many as it spans, from the nut where it fits there and otherwise from
 * its lowest stopped fret ([ChordVoicings.baseFret], which a written definition agrees with). A barre is a finger
 * the fingering names on several strings at one fret, or, for a shape that names no fingers, the lowest stopped fret
 * where two strings or more are held at it with no open or muted string between them.
 */
internal fun chordDiagramGeometryOf(shape: ChordVoicing, instrument: ChordInstrument, root: Int): ChordDiagramGeometry = when (shape) {
    is ChordVoicing.Keys -> ChordDiagramGeometry.Keyboard(
        octaves = maxOf(MIN_OCTAVES, ((listOfNotNull(shape.bass) + shape.notes).maxOrNull() ?: 0) / 12 + 1),
        keys = shape.notes.toSet(),
        roots = shape.notes.filter { it % 12 == root }.toSet(),
        bass = shape.bass,
    )
    is ChordVoicing.Fretted -> {
        val frets = shape.frets
        val baseFret = ChordVoicings.baseFret(frets)
        val stopped = frets.filterNotNull().filter { it > 0 }
        val fretCount = maxOf(MIN_FRETS, (stopped.maxOrNull() ?: 0) - baseFret + 1)
        val barres = barresOf(frets, shape.fingers)
        val heldByBarre = barres.flatMap { barre -> (barre.fromString..barre.toString).filter { frets[it] == barre.row + baseFret } }.toSet()
        ChordDiagramGeometry.Fretted(
            strings = frets.size,
            baseFret = baseFret,
            fretCount = fretCount,
            markers = frets.mapIndexed { string, fret ->
                when (fret) {
                    null -> ChordDiagramGeometry.Fretted.Marker.MUTED
                    0 -> if (isRootAt(instrument, string, 0, root)) ChordDiagramGeometry.Fretted.Marker.OPEN_ROOT else ChordDiagramGeometry.Fretted.Marker.OPEN
                    else -> null
                }
            },
            dots = frets.indices.filter { (frets[it] ?: 0) > 0 && (it !in heldByBarre || isRootAt(instrument, it, frets[it]!!, root)) }.map { string ->
                ChordDiagramGeometry.Fretted.Dot(
                    string = string,
                    row = frets[string]!! - baseFret,
                    finger = shape.fingers?.getOrNull(string)?.takeIf { it > 0 && string !in heldByBarre },
                    isRoot = isRootAt(instrument, string, frets[string]!!, root),
                )
            },
            barres = barres,
        )
    }
}

private fun isRootAt(instrument: ChordInstrument, string: Int, fret: Int, root: Int) =
    instrument.tuning.getOrNull(string)?.let { (it + fret) % 12 == root } == true

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
private const val MIN_OCTAVES = 2
