/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.scales

import androidx.compose.runtime.Immutable
import com.pandulapeter.campfire.chordpro.scales.Scale
import com.pandulapeter.campfire.chordpro.scales.Scales
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.ui.chords.ChordDiagramGeometry
import kotlin.math.abs

/**
 * What a scale diagram draws, in the diagram's own units: the strings and frets a scale's notes stand on, or the keys
 * it presses. Pure, so that it can be tested.
 */
@Immutable
internal sealed interface ScaleDiagramGeometry {

    /**
     * A neck seen from the front, the highest string on top as a player looking down at it reads tablature, the nut at
     * the left and [fretCount] frets to the right of it.
     *
     * @property strings How many rows of strings there are.
     * @property isFretless Whether the neck has no frets (the violin): its positions are drawn as faint dashed lines.
     * @property stringStarts Per row, the fret the string begins at: 0 for a string running from the nut, 5 for the banjo's
     *   drone, whose peg stands at the fifth fret.
     * @property stringWeights Per row, how thick the string is drawn, from 0 for the highest one to 1 for the lowest.
     * @property notes Every note of the scale on every string, a string's open note at the fret it starts at.
     * @property markers The frets numbered under the neck and, where it has frets, marked with an inlay, the twelfth
     *   with a double one: the guitar's and bass's at 3, 5, 7, 9 and 12, the shorter necks' at 10 rather than 9.
     * @property capo The fret a song's capo is on, the frets below which are dimmed, since nothing under it can be played:
     *   the notes stay where they sound, so the scale is the song's own on the whole neck. 0 for none.
     */
    data class Fretboard(
        val strings: Int,
        val fretCount: Int,
        val isFretless: Boolean,
        val stringStarts: List<Int>,
        val stringWeights: List<Float>,
        val notes: List<Note>,
        val markers: List<Int>,
        val capo: Int = 0,
    ) : ScaleDiagramGeometry {

        /**
         * @property row The string, from the top.
         * @property degree Which of the scale's notes this is, by its place in the scale from the root.
         */
        data class Note(val row: Int, val fret: Int, val degree: Int, val isRoot: Boolean)
    }

    /** [keyboard] with the scale's notes pressed across two octaves from a C, its roots marked. */
    data class Keyboard(val keyboard: ChordDiagramGeometry.Keyboard) : ScaleDiagramGeometry
}

/**
 * The geometry of [scale] played on [root], a pitch class, on [strings], the open strings of the player's tuning as
 * MIDI notes in the tuner's order (`InstrumentTuning.strings`): a neck where there are strings, else a keyboard (the
 * keyboard and Other). Every note within [FRET_COUNT] frets is placed, so that one octave of every string shows each
 * note of the scale at least once wherever the hand is. A [capo] dims the frets below it ([ScaleDiagramGeometry.Fretboard.capo]),
 * on every neck but the fretless one, which takes none.
 */
internal fun scaleDiagramGeometryOf(
    scale: Scale,
    root: Int,
    strings: List<Int>?,
    instrument: UserPreferences.Instrument,
    capo: Int = 0,
): ScaleDiagramGeometry {
    if (strings.isNullOrEmpty() || instrument == UserPreferences.Instrument.KEYBOARD || instrument == UserPreferences.Instrument.OTHER) {
        val pitchClasses = Scales.pitchClasses(scale, root)
        return ScaleDiagramGeometry.Keyboard(
            ChordDiagramGeometry.Keyboard(
                octaves = KEYBOARD_OCTAVES,
                keys = (0 until KEYBOARD_OCTAVES * 12).filter { it % 12 in pitchClasses }.toSet(),
                roots = (0 until KEYBOARD_OCTAVES * 12).filter { it % 12 == root.mod(12) }.toSet(),
                bass = null,
            ),
        )
    }
    // The tuner lists the strings from the highest numbered one, which tablature draws at the bottom: the low E of a
    // guitar, and the re-entrant g of a ukulele and the banjo's drone, which are strung there too.
    val rows = strings.reversed()
    val starts = rows.indices.map { row -> if (instrument == UserPreferences.Instrument.BANJO && row == rows.lastIndex) BANJO_DRONE_FRET else 0 }
    val pitchRanks = rows.map { pitch -> rows.count { it > pitch } }
    val notes = rows.flatMapIndexed { row, open ->
        (starts[row]..FRET_COUNT).mapNotNull { fret ->
            val interval = (open + fret - starts[row] - root).mod(12)
            val degree = scale.intervals.indexOf(interval)
            if (degree < 0) null else ScaleDiagramGeometry.Fretboard.Note(row = row, fret = fret, degree = degree, isRoot = degree == 0)
        }
    }
    return ScaleDiagramGeometry.Fretboard(
        strings = rows.size,
        fretCount = FRET_COUNT,
        isFretless = instrument == UserPreferences.Instrument.VIOLIN,
        stringStarts = starts,
        stringWeights = pitchRanks.map { rank -> if (rows.size > 1) rank / (rows.size - 1f) else 0f },
        notes = notes,
        markers = when (instrument) {
            UserPreferences.Instrument.UKULELE, UserPreferences.Instrument.BANJO, UserPreferences.Instrument.MANDOLIN -> SHORT_NECK_MARKERS
            else -> GUITAR_MARKERS
        },
        capo = if (instrument == UserPreferences.Instrument.VIOLIN) 0 else capo.coerceIn(0, FRET_COUNT),
    )
}

/**
 * How far the note at [fret] of a string starting at [start] is drawn as ringing open, from 0 (a stopped note's dot) to 1
 * (an open string's ring), under a capo on [capo], which is between two frets while it slides: the string's own open
 * note always, and under a capo the note at its fret, which the capo stops for the hand, so that the strings ringing at
 * the capo read as they do at the nut. A note passes from one to the other as the capo slides over its fret.
 */
internal fun scaleNoteOpenness(fret: Int, start: Int, capo: Float): Float = when {
    fret == start -> 1f
    capo <= 0f || fret < start -> 0f
    else -> (1f - abs(capo - fret)).coerceIn(0f, 1f)
}

/**
 * The frets every neck is drawn with: an octave, so every note of a scale is on every string at least once, and the
 * same for every scale and instrument, so that two cells side by side read alike.
 */
internal const val FRET_COUNT = 12

private const val KEYBOARD_OCTAVES = 2
private val GUITAR_MARKERS = listOf(3, 5, 7, 9, 12)
private val SHORT_NECK_MARKERS = listOf(3, 5, 7, 10, 12)
private const val BANJO_DRONE_FRET = 5
