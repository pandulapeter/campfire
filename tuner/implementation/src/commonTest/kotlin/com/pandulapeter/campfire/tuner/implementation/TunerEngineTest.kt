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
import com.pandulapeter.campfire.tuner.api.model.TunerConfig
import com.pandulapeter.campfire.tuner.api.model.TunerListening
import com.pandulapeter.campfire.tuner.api.model.TunerStopReason
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TunerEngineTest {
    private val input = FakeAudioInput()
    private val output = FakeToneOutput()
    private val chromatic = TunerConfig(referencePitch = 440, tuning = null)

    @Test
    fun `listening opens the input and reads what it hears`() = runTest {
        val engine = engine()
        engine.listen(chromatic)
        runCurrent()
        assertEquals(TunerListening.Hearing(reading = null, issue = null), engine.state.value.listening)
        input.signal = sine(330f)
        advanceTimeBy(1_000)
        val reading = assertNotNull(engine.hearing().reading)
        assertEquals(64, reading.note)
        assertTrue(abs(reading.cents) < 5f, "${reading.cents}")
    }

    @Test
    fun `a refused start stops with its reason`() = runTest {
        input.result = AudioInputStart.Refused(TunerStopReason.PERMISSION_DENIED)
        val engine = engine()
        engine.listen(chromatic)
        runCurrent()
        assertEquals(TunerListening.Stopped(TunerStopReason.PERMISSION_DENIED), engine.state.value.listening)
    }

    @Test
    fun `a stop while the start is pending stops, and the late answer is ignored`() = runTest {
        val gate = CompletableDeferred<Unit>()
        input.gate = gate
        val engine = engine()
        engine.listen(chromatic)
        runCurrent()
        assertEquals(TunerListening.Starting, engine.state.value.listening)
        engine.stopListening()
        runCurrent()
        assertEquals(TunerListening.Stopped(null), engine.state.value.listening)
        gate.complete(Unit)
        runCurrent()
        assertEquals(TunerListening.Stopped(null), engine.state.value.listening)
        assertEquals(1, input.stopCount)
    }

    @Test
    fun `a start that answers after a stop closes the input it opened`() = runTest {
        val gate = CompletableDeferred<Unit>()
        input.gate = gate
        input.isStartNonCancellable = true
        val engine = engine()
        engine.listen(chromatic)
        runCurrent()
        engine.stopListening()
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertEquals(TunerListening.Stopped(null), engine.state.value.listening)
        assertEquals(2, input.stopCount)
    }

    @Test
    fun `a disconnected input is opened again once`() = runTest {
        val engine = engine()
        engine.listen(chromatic)
        runCurrent()
        assertNotNull(input.listener).onLost(TunerStopReason.MICROPHONE_DISCONNECTED)
        runCurrent()
        assertIs<TunerListening.Hearing>(engine.state.value.listening)
        assertEquals(2, input.startCount)
        advanceTimeBy(500)
        assertNotNull(input.listener).onLost(TunerStopReason.MICROPHONE_DISCONNECTED)
        runCurrent()
        assertEquals(TunerListening.Stopped(TunerStopReason.MICROPHONE_DISCONNECTED), engine.state.value.listening)
        assertEquals(2, input.startCount)
    }

    @Test
    fun `a lost input of an earlier session is ignored`() = runTest {
        val engine = engine()
        engine.listen(chromatic)
        runCurrent()
        val earlier = assertNotNull(input.listener)
        engine.stopListening()
        engine.listen(chromatic)
        runCurrent()
        earlier.onLost(TunerStopReason.FAILED)
        runCurrent()
        assertIs<TunerListening.Hearing>(engine.state.value.listening)
    }

    @Test
    fun `an update while hearing reads against the new tuning`() = runTest {
        val engine = engine()
        input.signal = sine(Pitch.frequencyOf(46, 440))
        engine.listen(chromatic)
        advanceTimeBy(1_000)
        assertEquals(46, assertNotNull(engine.hearing().reading).note)
        engine.update(TunerConfig(referencePitch = 440, tuning = InstrumentTuning.GUITAR))
        advanceTimeBy(1_000)
        val reading = assertNotNull(engine.hearing().reading)
        assertEquals(45, reading.note)
        assertTrue(abs(reading.cents - 100f) < 5f, "${reading.cents}")
    }

    @Test
    fun `an update while stopped does not start listening`() = runTest {
        val engine = engine()
        engine.update(chromatic)
        runCurrent()
        assertEquals(TunerListening.Stopped(null), engine.state.value.listening)
        assertEquals(0, input.startCount)
    }

    @Test
    fun `a tone sounding hides the reading`() = runTest {
        val engine = engine()
        input.signal = sine(440f)
        engine.listen(chromatic)
        advanceTimeBy(1_000)
        assertNotNull(engine.hearing().reading)
        engine.playTone(note = 69, referencePitch = 440)
        advanceTimeBy(200)
        assertEquals(69, engine.state.value.tone)
        assertNull(engine.hearing().reading)
        engine.stopTone()
        output.isPlayable = false
        engine.playTone(note = 69, referencePitch = 440)
        runCurrent()
        assertNull(engine.state.value.tone)
    }

    @Test
    fun `a lost tone is no longer shown`() = runTest {
        val engine = engine()
        engine.playTone(note = 69, referencePitch = 440)
        runCurrent()
        assertEquals(69, engine.state.value.tone)
        assertNotNull(output.onLost).invoke()
        runCurrent()
        assertNull(engine.state.value.tone)
        assertEquals(1, output.stopCount)
    }

    /**
     * The engine runs in [TestScope.backgroundScope], which ends with the test: its poll never idles, so time is only
     * ever advanced by a given amount (never advanceUntilIdle). The scheduler's own time source is the engine's clock,
     * so that the tracker's time and the poll's delay move together.
     */
    private fun TestScope.engine() = TunerEngine(
        input = input,
        output = output,
        scope = backgroundScope,
        dispatcher = StandardTestDispatcher(testScheduler),
        timeSource = testScheduler.timeSource,
    )

    private fun TunerEngine.hearing() = assertIs<TunerListening.Hearing>(state.value.listening)

    private fun sine(frequency: Float): (FloatArray) -> Boolean = { window ->
        TestSignals.sine(frequency, FakeAudioInput.SAMPLE_RATE, window.size).copyInto(window)
        true
    }
}
