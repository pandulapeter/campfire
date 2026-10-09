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
import com.pandulapeter.campfire.tuner.api.model.InstrumentTuning
import kotlin.math.sqrt

/**
 * The McLeod pitch method over one window of the input: the normalized square difference function of the window, whose
 * first peak that comes close to the highest one is the period. That is what keeps it steady on a plucked string and
 * keeps it from reading an octave down, where an autocorrelation peak two periods away is about as high as the one at
 * one period. The autocorrelation is two FFTs of the window padded to twice its length, and every buffer is allocated
 * once, here.
 */
internal class PitchDetector(private val sampleRate: Int) {

    /** About 85 ms of the input, which holds two periods of anything above 24 Hz. */
    val windowSize = windowSizeFor(sampleRate)
    private val fft = Fft(windowSize * 2)
    private val real = FloatArray(windowSize * 2)
    private val imaginary = FloatArray(windowSize * 2)
    private val samples = FloatArray(windowSize)
    private val nsdf = FloatArray(windowSize / 2 + 2)

    /** Reads [window], the latest [windowSize] samples, for a pitch between [minFrequency] and [maxFrequency] Hz. */
    fun detect(window: FloatArray, minFrequency: Float, maxFrequency: Float): PitchEstimate {
        var mean = 0.0
        for (index in 0 until windowSize) mean += window[index]
        mean /= windowSize
        var energy = 0.0
        for (index in 0 until windowSize) {
            val sample = (window[index] - mean).toFloat()
            samples[index] = sample
            energy += sample * sample
        }
        val level = sqrt(energy / windowSize).toFloat()
        if (level < LEVEL_FLOOR) return PitchEstimate(frequency = null, clarity = 0f, level = level)
        val minLag = (sampleRate / maxFrequency).toInt().coerceAtLeast(2)
        val maxLag = (sampleRate / minFrequency).toInt().coerceAtMost(windowSize / 2)
        computeNsdf(maxLag + 1)
        val peak = pickPeak(minLag, maxLag) ?: return PitchEstimate(frequency = null, clarity = 0f, level = level)
        val (lag, clarity) = refined(peak)
        val frequency = sampleRate / lag
        return PitchEstimate(
            frequency = frequency.takeIf { clarity >= MIN_CLARITY && it in minFrequency..maxFrequency },
            clarity = clarity,
            level = level,
        )
    }

    /** `2·r(τ) / m(τ)` for every lag up to [lagCount], the autocorrelation `r` through the FFT and `m` by running sums. */
    private fun computeNsdf(lagCount: Int) {
        samples.copyInto(real)
        real.fill(0f, windowSize)
        imaginary.fill(0f)
        fft.transform(real, imaginary, inverse = false)
        for (index in real.indices) {
            real[index] = real[index] * real[index] + imaginary[index] * imaginary[index]
            imaginary[index] = 0f
        }
        fft.transform(real, imaginary, inverse = true)
        // The inverse transform leaves out the 1 / size, which cancels in the ratio below as long as m carries it too.
        val scale = real.size.toFloat()
        var squares = 0.0
        for (index in 0 until windowSize) squares += samples[index] * samples[index]
        var m = 2 * squares
        for (lag in 0 until lagCount) {
            if (lag > 0) {
                val leaving = samples[lag - 1]
                val leavingEnd = samples[windowSize - lag]
                m -= leaving * leaving + leavingEnd * leavingEnd
            }
            nsdf[lag] = if (m > 0) (2 * real[lag] / scale / m).toFloat() else 0f
        }
    }

    /**
     * The first key maximum - the highest point between a positive-going zero crossing and the next negative-going
     * one - that reaches [PEAK_THRESHOLD] of the highest of them, past the lobe around lag 0 and within the range.
     */
    private fun pickPeak(minLag: Int, maxLag: Int): Int? {
        var lag = 1
        while (lag < maxLag && nsdf[lag] > 0) lag++
        var highest = 0f
        var first = -1
        val maxima = IntArray(MAX_KEY_MAXIMA)
        var count = 0
        while (lag < maxLag && count < MAX_KEY_MAXIMA) {
            while (lag < maxLag && nsdf[lag] <= 0) lag++
            var best = -1
            while (lag < maxLag && nsdf[lag] > 0) {
                if (best < 0 || nsdf[lag] > nsdf[best]) best = lag
                lag++
            }
            // A lobe still rising at the end of the range has its peak beyond it.
            if (best < 0 || best >= maxLag - 1 || best < minLag) continue
            maxima[count++] = best
            if (nsdf[best] > highest) highest = nsdf[best]
        }
        for (index in 0 until count) {
            if (nsdf[maxima[index]] >= PEAK_THRESHOLD * highest) {
                first = maxima[index]
                break
            }
        }
        return first.takeIf { it > 0 }
    }

    /** The peak's lag and height, refined by a parabola through it and its two neighbours. */
    private fun refined(lag: Int): Pair<Float, Float> {
        val before = nsdf[lag - 1]
        val at = nsdf[lag]
        val after = nsdf[lag + 1]
        val curvature = before - 2 * at + after
        if (curvature >= 0f) return lag.toFloat() to at
        val shift = 0.5f * (before - after) / curvature
        return lag + shift to (at - 0.25f * (before - after) * shift).coerceAtMost(1f)
    }

    companion object {
        /** Below this RMS (−60 dBFS) nothing is read: there is nothing to read in it. */
        const val LEVEL_FLOOR = 0.001f
        const val MIN_CLARITY = 0.8f
        const val PEAK_THRESHOLD = 0.9f
        private const val MAX_KEY_MAXIMA = 64

        const val CHROMATIC_MIN_FREQUENCY = 30f
        const val CHROMATIC_MAX_FREQUENCY = 2_100f
        private const val SEMITONES_UNDER_LOWEST_STRING = 4
        private const val SEMITONES_OVER_HIGHEST_STRING = 12

        /** The power of two closest above 85 ms of [sampleRate]: 4096 frames at 44.1 and 48 kHz. */
        fun windowSizeFor(sampleRate: Int): Int {
            val frames = (sampleRate * 0.085).toInt()
            var size = 2
            while (size < frames) size *= 2
            return size
        }

        /**
         * The frequencies searched: B0 to C7 in chromatic mode, and from four semitones under a preset's lowest string to
         * an octave over its highest, which is most of what keeps a bass from being read an octave up.
         */
        fun rangeFor(tuning: InstrumentTuning?, referencePitch: Int): ClosedFloatingPointRange<Float> = if (tuning == null) {
            CHROMATIC_MIN_FREQUENCY..CHROMATIC_MAX_FREQUENCY
        } else {
            Pitch.frequencyOf(tuning.strings.min() - SEMITONES_UNDER_LOWEST_STRING, referencePitch)..Pitch.frequencyOf(tuning.strings.max() + SEMITONES_OVER_HIGHEST_STRING, referencePitch)
        }
    }
}
