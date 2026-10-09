/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.tuner.implementation

import com.pandulapeter.campfire.tuner.api.Pitch
import com.pandulapeter.campfire.tuner.api.model.TunerConfig
import com.pandulapeter.campfire.tuner.api.model.TunerReading
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

/**
 * Turns the thirty raw answers a second [PitchDetector] gives into a reading that can be read: the attack of a pluck
 * skipped, the median of the last few answers, the cents smoothed, a target that only moves once the new one has held,
 * the last reading held for a moment after the sound fades and in tune said only once it has stayed so. Pure, and
 * driven by the time it is handed rather than by a clock of its own, so that a test steps it frame by frame.
 */
internal class PitchTracker {

    private val recent = ArrayDeque<Float>()
    private var previousLevel = 0f
    private var previousTime: Long? = null
    private var attackUntil = Long.MIN_VALUE
    private var noiseFloor = 0f
    private var target: Int? = null
    private var candidate: Int? = null
    private var candidateCount = 0
    private var smoothedCents = 0f
    private var reading: TunerReading? = null
    private var lastHeardTime = Long.MIN_VALUE
    private var inTuneSince: Long? = null
    private var silentSince: Long? = null

    /** Takes the detector's answer for the window ending at [timeMillis] and returns what the display is to show. */
    fun step(estimate: PitchEstimate, timeMillis: Long, config: TunerConfig): TrackedPitch {
        val elapsed = previousTime?.let { timeMillis - it } ?: 0L
        previousTime = timeMillis
        val isSilent = updateSilence(estimate, timeMillis)
        if (estimate.level > previousLevel * ONSET_RATIO && estimate.level > PitchDetector.LEVEL_FLOOR) {
            attackUntil = timeMillis + ATTACK_MILLIS
            recent.clear()
        }
        previousLevel = estimate.level
        val frequency = estimate.frequency
        if (frequency == null) updateNoiseFloor(estimate.level, elapsed)
        val isHeard = frequency != null && timeMillis >= attackUntil && estimate.level >= noiseFloor * GATE_RATIO
        if (isHeard) {
            hear(frequency, timeMillis, elapsed, config)
        } else if (reading != null && timeMillis - lastHeardTime > HOLD_MILLIS) {
            release()
        }
        return TrackedPitch(reading = reading, isSilent = isSilent)
    }

    /** Forgets everything heard, for a new start or a new config whose target would not be comparable. */
    fun reset() {
        recent.clear()
        previousLevel = 0f
        previousTime = null
        attackUntil = Long.MIN_VALUE
        release()
        silentSince = null
    }

    private fun hear(frequency: Float, timeMillis: Long, elapsed: Long, config: TunerConfig) {
        lastHeardTime = timeMillis
        recent.addLast(frequency)
        if (recent.size > MEDIAN_SIZE) recent.removeFirst()
        val median = recent.sorted()[recent.size / 2]
        val heardTarget = Pitch.targetOf(median, config.tuning, config.referencePitch).note
        if (heardTarget == target) {
            candidate = null
            candidateCount = 0
        } else {
            if (heardTarget == candidate) candidateCount++ else {
                candidate = heardTarget
                candidateCount = 1
            }
            // Until the new target holds, whatever is shown stays: reading the new pitch against the old note would
            // show it hundreds of cents off for the frames it takes to be confirmed.
            if (candidateCount < TARGET_HOLD_COUNT) return
            target = heardTarget
            candidate = null
            candidateCount = 0
            smoothedCents = Pitch.centsBetween(median, heardTarget, config.referencePitch)
            inTuneSince = null
        }
        val note = target ?: return
        val cents = Pitch.centsBetween(median, note, config.referencePitch)
        smoothedCents += (cents - smoothedCents) * (1 - exp(-elapsed / SMOOTHING_MILLIS)).toFloat()
        if (abs(smoothedCents) <= IN_TUNE_CENTS) {
            if (inTuneSince == null) inTuneSince = timeMillis
        } else {
            inTuneSince = null
        }
        reading = TunerReading(
            note = note,
            cents = smoothedCents,
            frequency = Pitch.frequencyOf(note, config.referencePitch) * 2f.pow(smoothedCents / CENTS_PER_OCTAVE),
            isInTune = inTuneSince?.let { timeMillis - it >= IN_TUNE_DELAY_MILLIS } == true,
        )
    }

    private fun release() {
        recent.clear()
        target = null
        candidate = null
        candidateCount = 0
        reading = null
        inTuneSince = null
    }

    /** A floor that falls at once and rises slowly, from the windows with no pitch in them: the room, not the strings. */
    private fun updateNoiseFloor(level: Float, elapsed: Long) {
        noiseFloor = if (level <= noiseFloor) level else noiseFloor + (level - noiseFloor) * (elapsed / NOISE_FLOOR_RISE_MILLIS).coerceAtMost(1f)
    }

    private fun updateSilence(estimate: PitchEstimate, timeMillis: Long): Boolean {
        if (estimate.level != 0f) {
            silentSince = null
            return false
        }
        val since = silentSince ?: timeMillis.also { silentSince = it }
        return timeMillis - since >= SILENT_MILLIS
    }

    companion object {
        /** What the in-tune range is, either way. A constant to be settled by ear. */
        const val IN_TUNE_CENTS = 5f
        const val IN_TUNE_DELAY_MILLIS = 300L
        const val HOLD_MILLIS = 500L
        const val ATTACK_MILLIS = 60L
        const val SILENT_MILLIS = 2_000L
        const val TARGET_HOLD_COUNT = 3
        private const val MEDIAN_SIZE = 5
        private const val SMOOTHING_MILLIS = 80.0
        private const val NOISE_FLOOR_RISE_MILLIS = 2_000f

        /** A level twice the previous window's, 6 dB, is a new note being struck. */
        private const val ONSET_RATIO = 2f

        /** Twice the noise floor is the quietest a note may be and still be read. */
        private const val GATE_RATIO = 2f
        private const val CENTS_PER_OCTAVE = 1_200f
    }
}
