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

import com.pandulapeter.campfire.metronome.api.model.MetronomeSound
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClickSynthesizerTest {

    @Test
    fun everyVoiceIsShortAndPeaksBelowFullScale() {
        MetronomeSound.entries.forEach { sound ->
            ClickVoice.entries.forEach { voice ->
                val samples = ClickSynthesizer.render(sound, voice, SAMPLE_RATE)
                assertTrue(samples.size in 1..SAMPLE_RATE / 10, "$sound $voice is ${samples.size} samples long")
                assertTrue(samples.maxOf { abs(it) } <= 0.9f, "$sound $voice clips")
                assertTrue(samples.maxOf { abs(it) } > 0.05f, "$sound $voice is silent")
                assertTrue(abs(samples.first()) < 0.01f && abs(samples.last()) < 0.01f, "$sound $voice pops")
            }
        }
    }

    @Test
    fun theAccentIsLouderThanTheBeatAndTheBeatThanTheSubdivision() {
        MetronomeSound.entries.forEach { sound ->
            val (accent, normal, subdivision) = ClickVoice.entries.map { voice -> ClickSynthesizer.render(sound, voice, SAMPLE_RATE).maxOf { abs(it) } }
            assertTrue(accent > normal && normal > subdivision, "$sound")
        }
    }

    /**
     * The sounds are deterministic, so their 16-bit samples are compared with what they were when they were last
     * listened to. Never the floats: the JVM's sine may differ in the last bit between the interpreter and the JIT, which
     * quantizing hides.
     */
    @Test
    fun theSoundsAreWhatTheyWere() {
        val checksums = MetronomeSound.entries.associateWith { sound ->
            ClickVoice.entries.map { voice ->
                ClickSynthesizer.render(sound, voice, SAMPLE_RATE).fold(17L) { hash, sample -> hash * 31 + (sample * Short.MAX_VALUE).toInt() }
            }
        }
        assertEquals(GOLDEN_CHECKSUMS, checksums)
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
        val GOLDEN_CHECKSUMS = mapOf(
            MetronomeSound.CLICK to listOf(2972958089561375889, 6663599565728923646, 3255445391402671399),
            MetronomeSound.WOODBLOCK to listOf(8824197488272518872, -6625635541524748556, -7485555242030372509),
            MetronomeSound.BEEP to listOf(6273655871233221941, -348788883778118803, -1488791723267294119),
            MetronomeSound.STICKS to listOf(-2376280355717135335, -3953328323591712216, -149684563853939003),
            MetronomeSound.COWBELL to listOf(-1212724212882655896, 759244507372707989, -1869647261267855686),
        )
    }
}
