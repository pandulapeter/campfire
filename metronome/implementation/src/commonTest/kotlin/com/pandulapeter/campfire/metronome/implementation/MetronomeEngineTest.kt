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
import com.pandulapeter.campfire.metronome.api.model.MetronomeAudioIssue
import com.pandulapeter.campfire.metronome.api.model.MetronomeBeat
import com.pandulapeter.campfire.metronome.api.model.MetronomePattern
import com.pandulapeter.campfire.metronome.api.model.MetronomePlayback
import com.pandulapeter.campfire.metronome.api.model.MetronomeSound
import com.pandulapeter.campfire.metronome.api.model.MetronomeStopReason
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MetronomeEngineTest {

    private val pattern = MetronomePattern(bpm = 120)
    private val output = FakeAudioOutput()
    private val silentOutput = FakeAudioOutput()

    /**
     * The engine runs in [TestScope.backgroundScope], which ends with the test: its loop that releases the heard beats
     * never idles, so time is only ever advanced by a given amount.
     */
    private fun TestScope.engine() = MetronomeEngine(
        output = output,
        scope = backgroundScope,
        dispatcher = StandardTestDispatcher(testScheduler),
        createSilentOutput = { silentOutput },
    )

    @Test
    fun `a started click plays the pattern`() = runTest {
        val engine = engine()
        engine.start(pattern)
        runCurrent()
        assertEquals(MetronomePlayback.Playing(pattern, null), engine.playback.value)
        assertEquals(listOf(false), output.starts)
    }

    @Test
    fun `an output waiting for a gesture keeps saying so`() = runTest {
        output.result = AudioOutputStart.Started(MetronomeAudioIssue.WAITING_FOR_GESTURE)
        val engine = engine()
        engine.start(pattern)
        runCurrent()
        assertEquals(MetronomePlayback.Playing(pattern, MetronomeAudioIssue.WAITING_FOR_GESTURE), engine.playback.value)
    }

    @Test
    fun `a refused output stops the click with its reason`() = runTest {
        output.result = AudioOutputStart.Refused(MetronomeStopReason.AUDIO_REFUSED)
        val engine = engine()
        engine.start(pattern)
        runCurrent()
        assertEquals(MetronomePlayback.Stopped(MetronomeStopReason.AUDIO_REFUSED), engine.playback.value)
        assertEquals(emptyList(), silentOutput.starts)
    }

    @Test
    fun `no output runs the click silently`() = runTest {
        output.result = AudioOutputStart.Unavailable
        val engine = engine()
        engine.start(pattern)
        runCurrent()
        assertEquals(listOf(false), silentOutput.starts)
        assertEquals(MetronomePlayback.Playing(pattern, MetronomeAudioIssue.UNAVAILABLE), engine.playback.value)
    }

    @Test
    fun `a start while playing changes the pattern from the next bar without opening the output again`() = runTest {
        val engine = engine()
        engine.start(pattern)
        runCurrent()
        val faster = pattern.copy(bpm = 140)
        engine.start(faster)
        runCurrent()
        assertEquals(listOf(false), output.starts)
        assertEquals(MetronomePlayback.Playing(pattern, pendingPattern = faster), engine.playback.value)
    }

    @Test
    fun `an update keeps counting the bar, and one from the next bar counts bars again once it has ended`() = runTest {
        val engine = engine()
        val beats = beatsOf(engine)
        engine.start(pattern)
        runCurrent()
        output.heardFrame = Long.MAX_VALUE
        output.scheduleBeats(from = 0, to = 2)
        engine.update(pattern, fromNextBar = false)
        runCurrent()
        output.scheduleBeats(from = 2, to = 7)
        engine.update(pattern, fromNextBar = true)
        runCurrent()
        output.scheduleBeats(from = 7, to = 10)
        advanceTimeBy(RELEASE_STEP_MILLIS)
        runCurrent()
        assertEquals(listOf(0, 1, 2, 3, 0, 1, 2, 3, 0, 1), beats.map { it.beatIndex })
        assertEquals(listOf(0L, 0L, 0L, 0L, 1L, 1L, 1L, 1L, 0L, 0L), beats.map { it.barIndex })
        assertEquals(MetronomePlayback.Playing(pattern, null), engine.playback.value)
    }

    @Test
    fun `a change from the next bar is pending until its first click is heard`() = runTest {
        val engine = engine()
        val beats = beatsOf(engine)
        val next = MetronomePattern(bpm = 90)
        engine.start(pattern)
        runCurrent()
        output.scheduleBeats(from = 0, to = 2)
        engine.update(next, fromNextBar = true)
        runCurrent()
        assertEquals(MetronomePlayback.Playing(pattern, pendingPattern = next), engine.playback.value)
        output.scheduleBeats(from = 2, to = 5)
        output.heardFrame = 3 * BEAT_FRAMES
        advanceTimeBy(RELEASE_STEP_MILLIS)
        runCurrent()
        assertEquals(MetronomePlayback.Playing(pattern, pendingPattern = next), engine.playback.value)
        output.heardFrame = 4 * BEAT_FRAMES
        advanceTimeBy(RELEASE_STEP_MILLIS)
        runCurrent()
        assertEquals(MetronomePlayback.Playing(next), engine.playback.value)
        assertEquals(listOf(0, 1, 2, 3, 0), beats.map { it.beatIndex })
    }

    @Test
    fun `a tempo stepped while a change waits for the next bar replaces what waits`() = runTest {
        val engine = engine()
        val next = MetronomePattern(bpm = 90)
        val stepped = MetronomePattern(bpm = 95)
        engine.start(pattern)
        runCurrent()
        engine.update(next, fromNextBar = true)
        engine.update(stepped, fromNextBar = false)
        runCurrent()
        assertEquals(MetronomePlayback.Playing(pattern, pendingPattern = stepped), engine.playback.value)
    }

    @Test
    fun `a change waiting for the next bar applied now is heard from the next beat`() = runTest {
        val engine = engine()
        val beats = beatsOf(engine)
        val next = MetronomePattern(bpm = 90)
        engine.start(pattern)
        runCurrent()
        output.scheduleBeats(from = 0, to = 2)
        engine.update(next, fromNextBar = true)
        engine.applyPendingNow()
        runCurrent()
        output.stream!!.schedule(nowFrame = 2 * BEAT_FRAMES, untilFrame = 3 * BEAT_FRAMES) { _, _, _, _ -> }
        output.heardFrame = 2 * BEAT_FRAMES
        advanceTimeBy(RELEASE_STEP_MILLIS)
        runCurrent()
        assertEquals(MetronomePlayback.Playing(next), engine.playback.value)
        assertEquals(listOf(0, 1, 0), beats.map { it.beatIndex })
    }

    @Test
    fun `a lost output stops the click, unless it belongs to an earlier session`() = runTest {
        val engine = engine()
        engine.start(pattern)
        runCurrent()
        val earlier = output.listener!!
        engine.stop()
        engine.start(pattern)
        runCurrent()
        earlier.onLost(MetronomeStopReason.OUTPUT_DISCONNECTED)
        earlier.onAudioIssueChanged(MetronomeAudioIssue.WAITING_FOR_GESTURE)
        runCurrent()
        assertEquals(MetronomePlayback.Playing(pattern, null), engine.playback.value)
        output.listener!!.onAudioIssueChanged(MetronomeAudioIssue.WAITING_FOR_GESTURE)
        runCurrent()
        assertEquals(MetronomePlayback.Playing(pattern, MetronomeAudioIssue.WAITING_FOR_GESTURE), engine.playback.value)
        output.listener!!.onLost(MetronomeStopReason.OUTPUT_DISCONNECTED)
        runCurrent()
        assertEquals(MetronomePlayback.Stopped(MetronomeStopReason.OUTPUT_DISCONNECTED), engine.playback.value)
    }

    @Test
    fun `a beat is released once the output has played it`() = runTest {
        val engine = engine()
        val beats = beatsOf(engine)
        engine.start(pattern)
        runCurrent()
        output.stream!!.schedule(nowFrame = 0, untilFrame = FakeAudioOutput.SAMPLE_RATE.toLong()) { _, _, _, _ -> }
        advanceTimeBy(RELEASE_STEP_MILLIS)
        runCurrent()
        assertEquals(emptyList(), beats)
        output.heardFrame = 0
        advanceTimeBy(RELEASE_STEP_MILLIS)
        runCurrent()
        assertEquals(listOf(0), beats.map { it.beatIndex })
        output.heardFrame = FakeAudioOutput.SAMPLE_RATE / 2L
        advanceTimeBy(RELEASE_STEP_MILLIS)
        runCurrent()
        assertEquals(listOf(0, 1), beats.map { it.beatIndex })
        assertEquals(BeatLevel.ACCENT, beats.first().level)
    }

    @Test
    fun `a preview holds the output for a moment after the last one`() = runTest {
        val engine = engine()
        engine.preview(MetronomeSound.CLICK, BeatLevel.ACCENT)
        runCurrent()
        assertEquals(listOf(true), output.starts)
        advanceTimeBy(1_000)
        engine.preview(MetronomeSound.BEEP, BeatLevel.NORMAL)
        runCurrent()
        assertEquals(listOf(true), output.starts)
        advanceTimeBy(1_400)
        runCurrent()
        assertEquals(0, output.stopCount)
        advanceTimeBy(200)
        runCurrent()
        assertEquals(1, output.stopCount)
    }

    @Test
    fun `a start right after a preview keeps playing past the preview's hold`() = runTest {
        val engine = engine()
        engine.preview(MetronomeSound.CLICK, BeatLevel.ACCENT)
        runCurrent()
        engine.start(pattern)
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(listOf(true, false), output.starts)
        assertEquals(1, output.stopCount)
        assertEquals(MetronomePlayback.Playing(pattern, null), engine.playback.value)
    }

    @Test
    fun `a preview while playing is mixed into the click without opening the output again`() = runTest {
        val engine = engine()
        engine.start(pattern)
        runCurrent()
        engine.preview(MetronomeSound.BEEP, BeatLevel.NORMAL)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(listOf(false), output.starts)
        assertEquals(0, output.stopCount)
        assertEquals(MetronomePlayback.Playing(pattern, null), engine.playback.value)
    }

    @Test
    fun `a refused preview leaves the click stopped and the next one asks again`() = runTest {
        output.result = AudioOutputStart.Refused(MetronomeStopReason.AUDIO_REFUSED)
        val engine = engine()
        engine.preview(MetronomeSound.CLICK, BeatLevel.ACCENT)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(MetronomePlayback.Stopped(), engine.playback.value)
        assertEquals(0, output.stopCount)
        engine.preview(MetronomeSound.CLICK, BeatLevel.ACCENT)
        runCurrent()
        assertEquals(listOf(true, true), output.starts)
    }

    @Test
    fun `a stop closes the output`() = runTest {
        val engine = engine()
        engine.start(pattern)
        runCurrent()
        engine.stop()
        runCurrent()
        assertEquals(1, output.stopCount)
        assertEquals(MetronomePlayback.Stopped(), engine.playback.value)
    }

    @Test
    fun `no beat is released after a stop, however far the output's position runs`() = runTest {
        val engine = engine()
        val beats = beatsOf(engine)
        engine.start(pattern)
        runCurrent()
        output.scheduleBeats(from = 0, to = 4)
        engine.stop()
        runCurrent()
        output.heardFrame = Long.MAX_VALUE
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(emptyList(), beats)
    }

    private fun TestScope.beatsOf(engine: MetronomeEngine) = mutableListOf<MetronomeBeat>().also { beats ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { engine.beats.toList(beats) }
    }

    /** Has the output take the ticks of beats [from] to [to] (exclusive) of [pattern], the way a pull of its queue would. */
    private fun FakeAudioOutput.scheduleBeats(from: Int, to: Int) =
        stream!!.schedule(nowFrame = from * BEAT_FRAMES, untilFrame = to * BEAT_FRAMES) { _, _, _, _ -> }

    private companion object {
        const val RELEASE_STEP_MILLIS = 6L
        const val BEAT_FRAMES = FakeAudioOutput.SAMPLE_RATE / 2L
    }
}
