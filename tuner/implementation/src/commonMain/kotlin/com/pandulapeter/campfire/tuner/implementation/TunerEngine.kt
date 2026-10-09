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
import com.pandulapeter.campfire.tuner.api.Tuner
import com.pandulapeter.campfire.tuner.api.model.TunerConfig
import com.pandulapeter.campfire.tuner.api.model.TunerInputIssue
import com.pandulapeter.campfire.tuner.api.model.TunerListening
import com.pandulapeter.campfire.tuner.api.model.TunerState
import com.pandulapeter.campfire.tuner.api.model.TunerStopReason
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

/**
 * The tuner's state machine behind [TunerImpl], built with the [scope] it runs in and the [dispatcher] its calls are
 * confined to, so that a test can drive it on virtual time. Every call is handed to one coroutine at a time, as the
 * metronome's are, and each start is a session whose late listener callbacks are ignored.
 *
 * While listening it polls the input for its latest window about thirty times a second, runs the detector and the
 * tracker over it, and publishes a state only when it differs.
 */
internal class TunerEngine(
    private val input: AudioInput,
    private val output: ToneOutput,
    private val scope: CoroutineScope,
    dispatcher: CoroutineDispatcher,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : Tuner {

    private val confined = dispatcher.limitedParallelism(1)
    private val _state = MutableStateFlow(TunerState())
    override val state = _state.asStateFlow()
    private var config: TunerConfig? = null
    private var session = 0
    private var toneSession = 0
    private var job: Job? = null
    private var gestureIssue: TunerInputIssue? = null
    private var canReopen = true

    override fun listen(config: TunerConfig) = onEngine {
        val current = _state.value.listening
        this.config = config
        if (current is TunerListening.Hearing || current is TunerListening.Starting) return@onEngine
        canReopen = true
        open()
    }

    private fun open() {
        val sessionId = ++session
        setListening(TunerListening.Starting)
        job = scope.launch(confined) {
            when (val result = input.start(listenerFor(sessionId))) {
                is AudioInputStart.Refused -> if (sessionId == session) setListening(TunerListening.Stopped(result.reason))
                is AudioInputStart.Started -> if (sessionId == session) {
                    gestureIssue = result.issue
                    setListening(TunerListening.Hearing(issue = result.issue))
                    hear(sessionId, result.sampleRate)
                } else {
                    // Stopped while the web's answer was on its way: the input that just opened belongs to nobody.
                    input.stop()
                }
            }
        }
    }

    override fun update(config: TunerConfig) = onEngine {
        if (this.config != null && _state.value.listening !is TunerListening.Stopped) this.config = config
    }

    override fun stopListening() = onEngine {
        closeInput()
        setListening(TunerListening.Stopped())
    }

    override fun playTone(note: Int, referencePitch: Int) = onEngine {
        val toneId = ++toneSession
        val frequency = Pitch.frequencyOf(note, referencePitch)
        val isPlaying = output.play(
            render = { sampleRate -> ToneSynthesizer.loopOf(frequency, sampleRate) },
            onLost = { onEngine { if (toneId == toneSession) stopToneNow() } },
        )
        _state.value = _state.value.copy(tone = note.takeIf { isPlaying })
    }

    override fun stopTone() = onEngine { stopToneNow() }

    override fun setStartable(isStartable: Boolean) = onEngine { input.setGestureListening(isStartable) }

    private suspend fun hear(sessionId: Int, sampleRate: Int) {
        val detector = PitchDetector(sampleRate)
        val tracker = PitchTracker()
        val window = FloatArray(detector.windowSize)
        val start = timeSource.markNow()
        var trackedConfig = config
        while (sessionId == session) {
            val config = config ?: break
            if (config != trackedConfig) {
                trackedConfig = config
                tracker.reset()
            }
            if (input.latest(window)) {
                val range = PitchDetector.rangeFor(config.tuning, config.referencePitch)
                val estimate = detector.detect(window, range.start, range.endInclusive)
                val tracked = tracker.step(estimate, start.elapsedNow().inWholeMilliseconds, config)
                // What the microphone hears while a tone sounds is the speaker, so it is not read.
                setListening(
                    TunerListening.Hearing(
                        reading = tracked.reading.takeIf { _state.value.tone == null },
                        issue = gestureIssue ?: TunerInputIssue.SILENT.takeIf { tracked.isSilent },
                    )
                )
            }
            delay(POLL_INTERVAL_MILLIS)
        }
    }

    private fun listenerFor(sessionId: Int) = object : AudioInputListener {

        override fun onLost(reason: TunerStopReason) = onEngine {
            if (sessionId != session) return@onEngine
            closeInput()
            // Headphones plugged in or pulled change the route under the input, which is opened again on the new one,
            // once: an input that keeps going away is reported.
            if (reason == TunerStopReason.MICROPHONE_DISCONNECTED && canReopen) {
                canReopen = false
                open()
            } else {
                setListening(TunerListening.Stopped(reason))
            }
        }

        override fun onIssueChanged(issue: TunerInputIssue?) = onEngine {
            if (sessionId != session) return@onEngine
            gestureIssue = issue
            (_state.value.listening as? TunerListening.Hearing)?.let { setListening(it.copy(issue = issue)) }
        }
    }

    private fun stopToneNow() {
        toneSession++
        output.stop()
        _state.value = _state.value.copy(tone = null)
    }

    private fun closeInput() {
        session++
        job?.cancel()
        job = null
        gestureIssue = null
        input.stop()
    }

    private fun setListening(listening: TunerListening) {
        if (_state.value.listening != listening) _state.value = _state.value.copy(listening = listening)
    }

    private fun onEngine(action: suspend () -> Unit) {
        scope.launch(confined) { action() }
    }

    private companion object {
        const val POLL_INTERVAL_MILLIS = 33L
    }
}
