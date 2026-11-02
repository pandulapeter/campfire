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

import com.pandulapeter.campfire.tuner.api.model.TunerConfig
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every recording in `resources/tuner/`, named by the note it holds (`E2.wav`, `F#3 phone.wav`), read the way the
 * tuner reads the microphone: a window every 33 ms through the detector and the tracker, in chromatic mode. Whatever
 * the tracker says along the way has to be that note - no octave jump as the string dies away. A string recorded on a
 * real phone becomes a test by being dropped in; with none there, there is nothing to check.
 */
class RecordedStringsTest {

    @Test
    fun `every recorded string is read as the note it is named by`() = recordings().forEach { file ->
        val expected = noteOf(file.name)
        val (samples, sampleRate) = readWav(file)
        val detector = PitchDetector(sampleRate)
        val tracker = PitchTracker()
        val window = FloatArray(detector.inputSize)
        val step = sampleRate * STEP_MILLIS / 1_000
        val config = TunerConfig(referencePitch = 440, tuning = null)
        val range = PitchDetector.rangeFor(null, 440)
        val notes = mutableListOf<Int>()
        var end = detector.inputSize
        while (end <= samples.size) {
            samples.copyInto(window, 0, end - detector.inputSize, end)
            val estimate = detector.detect(window, range.start, range.endInclusive)
            tracker.step(estimate, end * 1_000L / sampleRate, config).reading?.let { notes += it.note }
            end += step
        }
        assertTrue(notes.isNotEmpty(), "${file.name}: nothing read")
        assertEquals(setOf(expected), notes.toSet(), file.name)
    }

    private fun recordings() = javaClass.getResource("/tuner")?.let { File(it.toURI()) }?.listFiles { file -> file.extension == "wav" }.orEmpty().sorted()

    /** The MIDI note a file name starts with: a letter, an optional `#` or `b`, and the octave. */
    private fun noteOf(name: String): Int {
        val match = requireNotNull(Regex("^([A-G])([#b]?)(-?\\d)").find(name)) { "$name names no note" }
        val (letter, accidental, octave) = match.destructured
        val pitchClass = "C D EF G A B".indexOf(letter) + when (accidental) {
            "#" -> 1
            "b" -> -1
            else -> 0
        }
        return (octave.toInt() + 1) * 12 + pitchClass
    }

    /** A 16-bit PCM WAV, its first channel only, as samples of full scale ±1 and its rate. */
    private fun readWav(file: File): Pair<FloatArray, Int> {
        val buffer = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(12)
        var sampleRate = 0
        var channels = 1
        while (buffer.remaining() >= 8) {
            val id = ByteArray(4).also(buffer::get).decodeToString()
            val size = buffer.int
            when (id) {
                "fmt " -> {
                    val start = buffer.position()
                    require(buffer.short.toInt() == 1) { "${file.name} is not PCM" }
                    channels = buffer.short.toInt()
                    sampleRate = buffer.int
                    buffer.position(start + 14)
                    require(buffer.short.toInt() == 16) { "${file.name} is not 16-bit" }
                    buffer.position(start + size)
                }
                "data" -> {
                    val frames = size / (2 * channels)
                    return FloatArray(frames) { buffer.getShort(buffer.position() + it * 2 * channels) / 32_768f } to sampleRate
                }
                else -> buffer.position(buffer.position() + size + size % 2)
            }
        }
        error("${file.name} has no data")
    }

    private companion object {
        const val STEP_MILLIS = 33
    }
}
