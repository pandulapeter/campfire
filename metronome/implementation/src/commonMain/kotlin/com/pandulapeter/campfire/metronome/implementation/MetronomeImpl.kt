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

import com.pandulapeter.campfire.metronome.api.Metronome
import com.pandulapeter.campfire.metronome.api.model.BeatLevel
import com.pandulapeter.campfire.metronome.api.model.MetronomeAudioIssue
import com.pandulapeter.campfire.metronome.api.model.MetronomeBeat
import com.pandulapeter.campfire.metronome.api.model.MetronomePattern
import com.pandulapeter.campfire.metronome.api.model.MetronomePlayback
import com.pandulapeter.campfire.metronome.api.model.MetronomeSound
import com.pandulapeter.campfire.metronome.api.model.MetronomeStopReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single

/**
 * The engine: a singleton with a scope of its own, as the sync repository is, so that a click belongs to the app.
 *
 * Every call is handed to one coroutine at a time ([confined]), whichever thread it came from - the UI's, a platform
 * callback's, a media button's - so the state below needs no lock and calls take effect in the order they were made.
 * Each start is a session of its own, and what a listener reports about an earlier session is ignored, since an
 * output that was just stopped may still be finishing a callback.
 */
@Single
internal class MetronomeImpl(private val output: AudioOutput) : Metronome {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val confined = Dispatchers.Default.limitedParallelism(1)
    private val silentOutput = SilentAudioOutput(scope)
    private val _playback = MutableStateFlow<MetronomePlayback>(MetronomePlayback.Stopped())
    override val playback = _playback.asStateFlow()
    private val _beats = MutableSharedFlow<MetronomeBeat>(extraBufferCapacity = BEAT_BUFFER, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val beats = _beats.asSharedFlow()
    private var activeOutput: AudioOutput? = null
    private var stream: ClickStream? = null
    private var session = 0
    private var releaseJob: Job? = null
    private var previewEndJob: Job? = null

    override fun start(pattern: MetronomePattern) = onEngine {
        val playing = _playback.value as? MetronomePlayback.Playing
        if (playing != null) {
            stream?.update(pattern, restartBar = true)
            _playback.value = playing.copy(pattern = pattern)
            return@onEngine
        }
        closeOutput()
        val sessionId = ++session
        val listener = listenerFor(sessionId)
        val result = output.start(isPreview = false, createStream = { ClickStream(it, pattern).also { stream -> this.stream = stream } }, listener = listener)
        _playback.value = when (result) {
            is AudioOutputStart.Started -> {
                activeOutput = output
                MetronomePlayback.Playing(pattern, result.audioIssue)
            }
            is AudioOutputStart.Refused -> {
                stream = null
                MetronomePlayback.Stopped(result.reason)
            }
            AudioOutputStart.Unavailable -> {
                silentOutput.start(isPreview = false, createStream = { ClickStream(it, pattern).also { stream -> this.stream = stream } }, listener = listener)
                activeOutput = silentOutput
                MetronomePlayback.Playing(pattern, MetronomeAudioIssue.UNAVAILABLE)
            }
        }
        if (_playback.value is MetronomePlayback.Playing) releaseJob = releaseHeardBeats()
    }

    override fun update(pattern: MetronomePattern, restartBar: Boolean) = onEngine {
        val playing = _playback.value as? MetronomePlayback.Playing ?: return@onEngine
        if (playing.pattern == pattern && !restartBar) return@onEngine
        stream?.update(pattern, restartBar)
        _playback.value = playing.copy(pattern = pattern)
    }

    override fun preview(sound: MetronomeSound, level: BeatLevel) = onEngine {
        if (_playback.value !is MetronomePlayback.Playing) {
            if (previewEndJob == null) {
                closeOutput()
                val result = output.start(
                    isPreview = true,
                    createStream = { ClickStream(it, pattern = null).also { stream -> this.stream = stream } },
                    listener = listenerFor(++session),
                )
                if (result !is AudioOutputStart.Started) {
                    stream = null
                    return@onEngine
                }
                activeOutput = output
            }
            // The output is kept for a moment after a preview rather than closed with its last sample, so that trying
            // the sounds one after the other does not take and give back the audio for every tap.
            previewEndJob?.cancel()
            previewEndJob = scope.launch(confined) {
                delay(PREVIEW_HOLD_MILLIS)
                previewEndJob = null
                closeOutput()
            }
        }
        stream?.preview(sound, level)
    }

    override fun stop() = onEngine {
        closeOutput()
        _playback.value = MetronomePlayback.Stopped()
    }

    override fun setStartable(isStartable: Boolean) = onEngine { output.setGestureListening(isStartable) }

    private fun onEngine(action: () -> Unit) {
        scope.launch(confined) { action() }
    }

    private fun listenerFor(sessionId: Int) = object : AudioOutputListener {

        override fun onLost(reason: MetronomeStopReason) = onEngine {
            if (sessionId != session) return@onEngine
            val wasPlaying = _playback.value is MetronomePlayback.Playing
            closeOutput()
            if (wasPlaying) _playback.value = MetronomePlayback.Stopped(reason)
        }

        override fun onAudioIssueChanged(issue: MetronomeAudioIssue?) = onEngine {
            if (sessionId != session) return@onEngine
            (_playback.value as? MetronomePlayback.Playing)?.let { _playback.value = it.copy(audioIssue = issue) }
        }
    }

    /**
     * Releases the ticks the output has been handed as its playback position passes them. Polled, since no platform
     * calls back at a click's moment; a few milliseconds is far below what the eye notices in a flash.
     */
    private fun releaseHeardBeats() = scope.launch(confined) {
        val stream = stream ?: return@launch
        val output = activeOutput ?: return@launch
        val pending = ArrayDeque<MetronomeSequencer.Tick>()
        while (isActive) {
            while (true) pending.addLast(stream.renderedTicks.tryReceive().getOrNull() ?: break)
            val heardFrame = output.heardFrame()
            while (pending.isNotEmpty() && pending.first().frame <= heardFrame) {
                val tick = pending.removeFirst()
                _beats.tryEmit(MetronomeBeat(tick.beatIndex, tick.barIndex, tick.level, tick.isSubdivision))
            }
            delay(RELEASE_INTERVAL_MILLIS)
        }
    }

    private fun closeOutput() {
        session++
        previewEndJob?.cancel()
        previewEndJob = null
        releaseJob?.cancel()
        releaseJob = null
        activeOutput?.stop()
        activeOutput = null
        stream = null
    }

    private companion object {
        const val BEAT_BUFFER = 16
        const val RELEASE_INTERVAL_MILLIS = 5L
        const val PREVIEW_HOLD_MILLIS = 1_500L
    }
}
