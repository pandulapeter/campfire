/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.metronome.implementation

import com.pandulapeter.campfire.metronome.api.model.BeatLevel
import com.pandulapeter.campfire.metronome.api.model.MetronomeSound
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** The three clicks every sound has. */
internal enum class ClickVoice {
    ACCENT,
    NORMAL,
    SUBDIVISION;

    companion object {

        /** The voice a preview of [level] plays; a muted level previews the normal one, since silence chooses nothing. */
        fun of(level: BeatLevel) = if (level == BeatLevel.ACCENT) ACCENT else NORMAL
    }
}

/**
 * Renders the samples of every click, once per sample rate. Everything is computed - decaying sines, a pair of
 * partials, filtered noise from a seeded [Random], which is the same generator on every platform - so the sounds are
 * identical everywhere and cost nothing to ship. Each click starts with a short ramp and ends with one, so neither end
 * pops, and peaks below full scale, so that a click mixed over the tail of the previous one does not clip.
 */
internal object ClickSynthesizer {

    fun render(sound: MetronomeSound, voice: ClickVoice, sampleRate: Int): FloatArray {
        val gain = when (voice) {
            ClickVoice.ACCENT -> 0.9f
            ClickVoice.NORMAL -> 0.65f
            ClickVoice.SUBDIVISION -> 0.4f
        }
        return when (sound) {
            MetronomeSound.CLICK -> tone(
                sampleRate = sampleRate,
                durationSeconds = 0.025,
                decaySeconds = 0.004,
                partials = listOf(voice.pick(2500.0, 1800.0, 1200.0) to 1.0),
                gain = gain,
            )
            MetronomeSound.WOODBLOCK -> tone(
                sampleRate = sampleRate,
                durationSeconds = 0.05,
                decaySeconds = 0.01,
                partials = voice.pick(1100.0, 900.0, 700.0).let { listOf(it to 1.0, it * 2.71 to 0.3) },
                gain = gain,
            )
            MetronomeSound.BEEP -> tone(
                sampleRate = sampleRate,
                durationSeconds = 0.06,
                decaySeconds = 0.06,
                partials = listOf(voice.pick(1760.0, 880.0, 660.0) to 1.0),
                gain = gain,
                attackSeconds = 0.002,
                releaseSeconds = 0.01,
            )
            MetronomeSound.STICKS -> noise(
                sampleRate = sampleRate,
                durationSeconds = 0.03,
                decaySeconds = 0.005,
                seed = voice.ordinal,
                gain = gain,
            )
            MetronomeSound.COWBELL -> tone(
                sampleRate = sampleRate,
                durationSeconds = 0.08,
                decaySeconds = 0.018,
                partials = voice.pick(587.0 to 845.0, 540.0 to 800.0, 540.0 to 800.0).let { (low, high) ->
                    listOf(low to 1.0, high to 0.8, low * 3 to 0.15, high * 3 to 0.1)
                },
                gain = gain,
            )
        }
    }

    private fun <T> ClickVoice.pick(accent: T, normal: T, subdivision: T) = when (this) {
        ClickVoice.ACCENT -> accent
        ClickVoice.NORMAL -> normal
        ClickVoice.SUBDIVISION -> subdivision
    }

    private fun tone(
        sampleRate: Int,
        durationSeconds: Double,
        decaySeconds: Double,
        partials: List<Pair<Double, Double>>,
        gain: Float,
        attackSeconds: Double = DEFAULT_ATTACK_SECONDS,
        releaseSeconds: Double = DEFAULT_RELEASE_SECONDS,
    ): FloatArray {
        val amplitudeSum = partials.sumOf { it.second }
        return render(sampleRate, durationSeconds, decaySeconds, gain, attackSeconds, releaseSeconds) { time ->
            partials.sumOf { (frequency, amplitude) -> amplitude * sin(2 * PI * frequency * time) } / amplitudeSum
        }
    }

    /** Noise passed through a first difference, which takes out the low end and leaves the crack of two sticks. */
    private fun noise(
        sampleRate: Int,
        durationSeconds: Double,
        decaySeconds: Double,
        seed: Int,
        gain: Float,
    ): FloatArray {
        val random = Random(NOISE_SEED + seed)
        var previous = 0.0
        return render(sampleRate, durationSeconds, decaySeconds, gain, DEFAULT_ATTACK_SECONDS, DEFAULT_RELEASE_SECONDS) {
            val sample = random.nextDouble() * 2 - 1
            ((sample - previous) / 2).also { previous = sample }
        }
    }

    private inline fun render(
        sampleRate: Int,
        durationSeconds: Double,
        decaySeconds: Double,
        gain: Float,
        attackSeconds: Double,
        releaseSeconds: Double,
        waveform: (time: Double) -> Double,
    ): FloatArray {
        val length = (durationSeconds * sampleRate).toInt()
        val attackFrames = (attackSeconds * sampleRate).coerceAtLeast(1.0)
        val releaseFrames = (releaseSeconds * sampleRate).coerceAtLeast(1.0)
        return FloatArray(length) { index ->
            val time = index.toDouble() / sampleRate
            val envelope = min(1.0, index / attackFrames) * min(1.0, (length - 1 - index) / releaseFrames) * exp(-time / decaySeconds)
            (gain * envelope * waveform(time)).toFloat()
        }
    }

    private const val DEFAULT_ATTACK_SECONDS = 0.0005
    private const val DEFAULT_RELEASE_SECONDS = 0.002
    private const val NOISE_SEED = 1_337
}
