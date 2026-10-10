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
import com.pandulapeter.campfire.metronome.api.model.MetronomePattern
import com.pandulapeter.campfire.metronome.api.model.MetronomeSound
import com.pandulapeter.campfire.metronome.api.model.Subdivision
import com.pandulapeter.campfire.metronome.api.model.TimeSignature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MetronomeSequencerTest {

    private fun sequencer(pattern: MetronomePattern) = MetronomeSequencer(SAMPLE_RATE, pattern)

    @Test
    fun `the clicks of a plain bar are placed by the tempo`() {
        val ticks = sequencer(MetronomePattern(bpm = 120)).ticksUntil(SAMPLE_RATE * 2L)
        assertEquals(listOf(0L, 24_000L, 48_000L, 72_000L), ticks.map { it.frame })
        assertEquals(listOf(0, 1, 2, 3), ticks.map { it.beatIndex })
        assertEquals(listOf(BeatLevel.ACCENT, BeatLevel.NORMAL, BeatLevel.NORMAL, BeatLevel.NORMAL), ticks.map { it.level })
    }

    @Test
    fun `the clicks of a compound bar are counted as the tempo`() {
        val ticks = sequencer(MetronomePattern(bpm = 120, timeSignature = TimeSignature(6, 8))).ticksUntil(SAMPLE_RATE * 3L)
        assertEquals(6, ticks.size)
        assertEquals(listOf(0, 1, 2, 3, 4, 5), ticks.map { it.beatIndex })
        assertEquals(BeatLevel.ACCENT, ticks[3].level)
    }

    @Test
    fun `an odd bar wraps into the next`() {
        val ticks = sequencer(MetronomePattern(bpm = 60, timeSignature = TimeSignature(7, 8))).ticksUntil(SAMPLE_RATE * 8L)
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 0), ticks.map { it.beatIndex })
        assertEquals(listOf(0L, 0L, 0L, 0L, 0L, 0L, 0L, 1L), ticks.map { it.barIndex })
    }

    @Test
    fun `a triplet subdivision cuts every beat in three`() {
        val ticks = sequencer(MetronomePattern(bpm = 60, subdivision = Subdivision.TRIPLETS)).ticksUntil(SAMPLE_RATE * 1L)
        assertEquals(listOf(0L, 16_000L, 32_000L), ticks.map { it.frame })
        assertEquals(listOf(false, true, true), ticks.map { it.isSubdivision })
    }

    @Test
    fun `a muted beat counts without sounding`() {
        val pattern = MetronomePattern(bpm = 60, beatLevels = listOf(BeatLevel.ACCENT, BeatLevel.MUTED, BeatLevel.NORMAL, BeatLevel.NORMAL))
        val ticks = sequencer(pattern.copy(subdivision = Subdivision.EIGHTHS)).ticksUntil(SAMPLE_RATE * 2L)
        assertEquals(4, ticks.size)
        assertEquals(listOf(ClickVoice.ACCENT, ClickVoice.SUBDIVISION, null, null), ticks.map { it.voice })
    }

    @Test
    fun `a tempo change mid-bar lands on the next click`() {
        val sequencer = sequencer(MetronomePattern(bpm = 60))
        sequencer.ticksUntil(1L)
        sequencer.update(MetronomePattern(bpm = 120), barChangeId = null)
        val ticks = sequencer.ticksUntil(SAMPLE_RATE * 2L)
        assertEquals(listOf(48_000L, 72_000L), ticks.map { it.frame })
        assertEquals(listOf(1, 2), ticks.map { it.beatIndex })
    }

    @Test
    fun `a timing change waits for the beat to end`() {
        val sequencer = sequencer(MetronomePattern(bpm = 60, subdivision = Subdivision.EIGHTHS))
        sequencer.ticksUntil(1L)
        sequencer.update(MetronomePattern(bpm = 120), barChangeId = null)
        assertEquals(listOf(24_000L, 48_000L, 72_000L), sequencer.ticksUntil(SAMPLE_RATE * 2L).map { it.frame })
    }

    @Test
    fun `a sound change lands on the next click`() {
        val sequencer = sequencer(MetronomePattern(bpm = 60, subdivision = Subdivision.EIGHTHS))
        sequencer.ticksUntil(1L)
        sequencer.update(MetronomePattern(bpm = 60, subdivision = Subdivision.EIGHTHS, sound = MetronomeSound.COWBELL), barChangeId = null)
        assertEquals(MetronomeSound.COWBELL, sequencer.ticksUntil(SAMPLE_RATE * 1L).single().sound)
    }

    @Test
    fun `a change from the next bar lets the bar being played end, then counts a new one from beat one`() {
        val sequencer = sequencer(MetronomePattern(bpm = 60))
        val before = sequencer.ticksUntil(SAMPLE_RATE * 6L)
        assertEquals(listOf(0L, 0L, 0L, 0L, 1L, 1L), before.map { it.barIndex })
        sequencer.update(MetronomePattern(bpm = 120, timeSignature = TimeSignature(3, 4)), barChangeId = 1)
        val ticks = sequencer.ticksUntil(SAMPLE_RATE * 10L)
        assertEquals(listOf(2, 3, 0, 1, 2, 0), ticks.map { it.beatIndex })
        assertEquals(listOf(1L, 1L, 0L, 0L, 0L, 1L), ticks.map { it.barIndex })
        assertEquals(listOf(6L, 7L, 8L).map { it * SAMPLE_RATE }, ticks.take(3).map { it.frame })
        assertEquals(listOf(8L * SAMPLE_RATE + 24_000, 8L * SAMPLE_RATE + 48_000), ticks.slice(3..4).map { it.frame })
        assertEquals(BeatLevel.ACCENT, ticks[2].level)
    }

    @Test
    fun `a change from the next bar made before a bar's first click starts with it`() {
        val sequencer = sequencer(MetronomePattern(bpm = 60))
        sequencer.ticksUntil(SAMPLE_RATE * 4L)
        sequencer.update(MetronomePattern(bpm = 120), barChangeId = 1)
        val ticks = sequencer.ticksUntil(SAMPLE_RATE * 5L)
        assertEquals(listOf(4L * SAMPLE_RATE, 4L * SAMPLE_RATE + 24_000), ticks.map { it.frame })
        assertEquals(listOf(0L, 0L), ticks.map { it.barIndex })
    }

    @Test
    fun `a tempo stepped while a change from the next bar waits waits with it`() {
        val sequencer = sequencer(MetronomePattern(bpm = 60))
        sequencer.ticksUntil(1L)
        sequencer.update(MetronomePattern(bpm = 120), barChangeId = 1)
        sequencer.update(MetronomePattern(bpm = 30), barChangeId = null)
        val ticks = sequencer.ticksUntil(SAMPLE_RATE * 7L)
        assertEquals(listOf(1L, 2L, 3L, 4L, 6L).map { it * SAMPLE_RATE }, ticks.map { it.frame })
        assertEquals(listOf(1, 2, 3, 0, 1), ticks.map { it.beatIndex })
    }

    @Test
    fun `a change waiting for the next bar applied now makes the next beat beat one`() {
        val sequencer = sequencer(MetronomePattern(bpm = 60))
        sequencer.ticksUntil(SAMPLE_RATE * 2L)
        sequencer.update(MetronomePattern(bpm = 120), barChangeId = 7)
        sequencer.applyPendingBarChangeNow()
        val ticks = sequencer.ticksUntil(SAMPLE_RATE * 3L)
        assertEquals(listOf(2L * SAMPLE_RATE, 2L * SAMPLE_RATE + 24_000), ticks.map { it.frame })
        assertEquals(listOf(0, 1), ticks.map { it.beatIndex })
        assertEquals(listOf(7, 7), ticks.map { it.barChangeId })
    }

    @Test
    fun `applying now with nothing waiting changes nothing`() {
        val sequencer = sequencer(MetronomePattern(bpm = 60))
        sequencer.ticksUntil(SAMPLE_RATE * 2L)
        sequencer.applyPendingBarChangeNow()
        sequencer.update(MetronomePattern(bpm = 60, timeSignature = TimeSignature(3, 4)), barChangeId = 1)
        val ticks = sequencer.ticksUntil(SAMPLE_RATE * 5L)
        assertEquals(listOf(2, 3, 0), ticks.map { it.beatIndex })
        assertEquals(listOf(null, null, 1), ticks.map { it.barChangeId })
    }

    @Test
    fun `a sound change waiting on the next bar still lands on the next click`() {
        val sequencer = sequencer(MetronomePattern(bpm = 60))
        sequencer.ticksUntil(1L)
        sequencer.update(MetronomePattern(bpm = 120, sound = MetronomeSound.COWBELL), barChangeId = 1)
        val ticks = sequencer.ticksUntil(SAMPLE_RATE * 2L)
        assertEquals(listOf(SAMPLE_RATE.toLong()), ticks.map { it.frame })
        assertEquals(MetronomeSound.COWBELL, ticks.single().sound)
    }

    @Test
    fun `a shorter bar after the current beat starts a new one`() {
        val sequencer = sequencer(MetronomePattern(bpm = 60))
        sequencer.ticksUntil(SAMPLE_RATE * 3L)
        sequencer.update(MetronomePattern(bpm = 60, timeSignature = TimeSignature(2, 4)), barChangeId = null)
        assertEquals(listOf(0, 1), sequencer.ticksUntil(SAMPLE_RATE * 5L).map { it.beatIndex })
    }

    @Test
    fun `the clicks do not drift over ten thousand bars`() {
        // 97 at 44.1 kHz is 27 278.35... frames a click, which rounded intervals would get wrong on every one of them.
        val sampleRate = 44_100
        val sequencer = MetronomeSequencer(sampleRate, MetronomePattern(bpm = 97, subdivision = Subdivision.TRIPLETS))
        val clicks = 10_000L * 4 * 3
        val frames = clicks * sampleRate * 60 / (97 * 3)
        var count = 0L
        var last = 0L
        var position = 0L
        while (position <= frames) {
            position += 4_410
            sequencer.ticksUntil(position).forEach {
                count++
                last = it.frame
            }
        }
        val exact = (count - 1).toDouble() * sampleRate * 60 / (97 * 3)
        assertTrue(kotlin.math.abs(last - exact) < 1.0, "$last is not within a frame of $exact")
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
    }
}
