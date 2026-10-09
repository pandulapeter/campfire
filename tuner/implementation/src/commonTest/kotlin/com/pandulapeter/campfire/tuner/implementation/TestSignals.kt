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

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** The sounds the detector and the tracker are tested with, every random one seeded. */
internal object TestSignals {

    fun sine(frequency: Float, sampleRate: Int, length: Int, amplitude: Float = 0.5f, phase: Double = 0.3) =
        FloatArray(length) { (amplitude * sin(2 * PI * frequency * it / sampleRate + phase)).toFloat() }

    /** A sum of harmonics with the given amplitudes, the first being the fundamental. */
    fun harmonics(frequency: Float, sampleRate: Int, length: Int, amplitudes: List<Float>) = FloatArray(length) { frame ->
        amplitudes.withIndex().sumOf { (index, amplitude) ->
            amplitude * sin(2 * PI * frequency * (index + 1) * frame / sampleRate + index * 0.7)
        }.toFloat() * 0.3f
    }

    /**
     * A plucked string: Karplus-Strong, a delay line of one period filled with noise and averaged as it circulates,
     * with an all-pass filter for the fraction of a sample the period has over its whole frames.
     */
    fun pluckedString(frequency: Float, sampleRate: Int, length: Int, seed: Int = 1, skip: Int = 0): FloatArray {
        val random = Random(seed)
        // The averaging reads the sample after the one leaving the line, which is half a sample short of a whole delay.
        val period = sampleRate / frequency.toDouble() + 0.5
        val delay = period.toInt()
        val fraction = period - delay
        val coefficient = ((1 - fraction) / (1 + fraction)).toFloat()
        val line = FloatArray(delay) { random.nextFloat() * 2 - 1 }
        // Noise without its mean, or the string carries a DC offset that the averaging keeps for ever.
        val mean = line.average().toFloat()
        for (index in line.indices) line[index] -= mean
        val output = FloatArray(length + skip)
        var index = 0
        var previousInput = 0f
        var previousOutput = 0f
        for (frame in output.indices) {
            val current = line[index]
            val next = line[(index + 1) % delay]
            val averaged = 0.498f * (current + next)
            val allPassed = coefficient * averaged + previousInput - coefficient * previousOutput
            previousInput = averaged
            previousOutput = allPassed
            line[index] = allPassed
            output[frame] = current * 0.5f
            index = (index + 1) % delay
        }
        return output.copyOfRange(skip, skip + length)
    }

    fun noise(length: Int, amplitude: Float, seed: Int = 2): FloatArray {
        val random = Random(seed)
        return FloatArray(length) { (random.nextFloat() * 2 - 1) * amplitude }
    }

    fun mixed(vararg signals: FloatArray) = FloatArray(signals.minOf { it.size }) { frame -> signals.sumOf { it[frame].toDouble() }.toFloat() }

    fun rms(signal: FloatArray) = sqrt(signal.sumOf { it.toDouble() * it } / signal.size).toFloat()

    /** [noise] scaled to be [decibels] under [signal]. */
    fun noiseUnder(signal: FloatArray, decibels: Float, seed: Int = 3): FloatArray {
        val noise = noise(signal.size, 1f, seed)
        val scale = rms(signal) / rms(noise) / 10f.pow(decibels / 20)
        return FloatArray(signal.size) { noise[it] * scale }
    }
}
