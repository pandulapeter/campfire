/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.pandulapeter.campfire.tuner.implementation

import com.pandulapeter.campfire.tuner.api.model.TunerInputIssue
import com.pandulapeter.campfire.tuner.api.model.TunerStopReason
import kotlinx.coroutines.await
import org.koin.core.annotation.Single

/**
 * `getUserMedia` with the echo cancellation, the noise suppression and the gain control off, into an `AnalyserNode` of
 * the tuner's own `AudioContext`, connected to nothing; [latest] is one `getFloatTimeDomainData`. The rejection's name
 * is the reason. A context that is not running is [TunerInputIssue.WAITING_FOR_GESTURE], resumed by the capture-phase
 * listener [WebTunerAudio] arms; ending the stream's tracks is what puts the browser's recording indicator out. Nothing
 * in the page calls back into Kotlin, so a track that ended and the context's state are looked at on every poll.
 */
@Single
internal class WebAudioInput : AudioInput {

    private var listener: AudioInputListener? = null
    private var isRunning = true

    override suspend fun start(listener: AudioInputListener): AudioInputStart {
        stop()
        WebTunerAudio.install()
        val reason = when (openTunerMicrophone().await<JsString>().toString()) {
            "ok" -> null
            "NotAllowedError", "SecurityError" -> TunerStopReason.PERMISSION_DENIED
            "NotFoundError", "OverconstrainedError" -> TunerStopReason.NO_MICROPHONE
            "NotReadableError", "AbortError" -> TunerStopReason.MICROPHONE_BUSY
            "NotSupportedError", "TypeError" -> TunerStopReason.NOT_SUPPORTED
            else -> TunerStopReason.FAILED
        }
        if (reason != null) return AudioInputStart.Refused(reason)
        this.listener = listener
        val sampleRate = tunerSampleRate()
        prepareTunerAnalyser(PitchDetector.windowSizeFor(sampleRate))
        isRunning = isTunerContextRunning()
        return AudioInputStart.Started(sampleRate, issue = if (isRunning) null else TunerInputIssue.WAITING_FOR_GESTURE)
    }

    override fun latest(window: FloatArray): Long {
        val listener = listener ?: return AudioInput.NO_WINDOW
        if (isTunerInputEnded()) {
            listener.onLost(TunerStopReason.MICROPHONE_DISCONNECTED)
            return AudioInput.NO_WINDOW
        }
        val running = isTunerContextRunning()
        if (running != isRunning) {
            isRunning = running
            listener.onIssueChanged(if (running) null else TunerInputIssue.WAITING_FOR_GESTURE)
        }
        if (!readTunerWindow()) return AudioInput.NO_WINDOW
        for (index in window.indices) window[index] = tunerSample(index)
        return tunerFramePosition().toLong()
    }

    /** Closes whatever is open, a request still waiting for the browser's answer included. */
    override fun stop() {
        listener = null
        WebTunerAudio.install()
        closeTunerMicrophone()
    }

    override fun setGestureListening(isEnabled: Boolean) = WebTunerAudio.setGestureListening(isEnabled)
}
