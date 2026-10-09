/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.tuner.api

import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning
import com.pandulapeter.campfire.tuner.api.model.NoteOffset
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/** Equal temperament, counted in MIDI note numbers (A4 is 69) from a reference pitch for A4 in Hz. */
public object Pitch {

    /** The reference pitches the tuner offers: a baroque A at the bottom and a semitone over concert pitch at the top. */
    public val REFERENCE_PITCH_RANGE: IntRange = 415..466

    public const val DEFAULT_REFERENCE_PITCH: Int = 440

    private const val A4 = 69
    private const val CENTS_PER_SEMITONE = 100f
    private const val SEMITONES_PER_OCTAVE = 12

    public fun frequencyOf(note: Int, referencePitch: Int): Float = (referencePitch * 2.0.pow((note - A4).toDouble() / SEMITONES_PER_OCTAVE)).toFloat()

    /** How far [frequency] is from [note], in cents: positive is sharp. */
    public fun centsBetween(frequency: Float, note: Int, referencePitch: Int): Float =
        (SEMITONES_PER_OCTAVE * CENTS_PER_SEMITONE * log2(frequency.toDouble() / frequencyOf(note, referencePitch))).toFloat()

    /** The nearest semitone to [frequency] and how far it is from it, never more than half a semitone. */
    public fun noteOf(frequency: Float, referencePitch: Int): NoteOffset {
        val note = (A4 + SEMITONES_PER_OCTAVE * log2(frequency.toDouble() / referencePitch)).roundToInt()
        return NoteOffset(note = note, cents = centsBetween(frequency, note, referencePitch))
    }

    /** The string of [tuning] nearest to [frequency], however far that is, and how far it is from it. */
    public fun nearestString(frequency: Float, tuning: InstrumentTuning, referencePitch: Int): NoteOffset = tuning.strings
        .map { NoteOffset(note = it, cents = centsBetween(frequency, it, referencePitch)) }
        .minBy { abs(it.cents) }

    /** The note [frequency] is read against: the nearest string of [tuning], or the nearest semitone without one. */
    public fun targetOf(frequency: Float, tuning: InstrumentTuning?, referencePitch: Int): NoteOffset =
        if (tuning == null) noteOf(frequency, referencePitch) else nearestString(frequency, tuning, referencePitch)
}
