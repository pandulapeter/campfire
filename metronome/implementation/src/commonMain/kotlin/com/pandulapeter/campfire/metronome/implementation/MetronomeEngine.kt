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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The click's state machine behind [MetronomeImpl], built with the [scope] it runs in, the [dispatcher] its calls are
 * confined to and the output a click falls back on where there is no audio, so that a test can drive it on virtual time.
 *
 * Every call is handed to one coroutine at a time ([confined]), whichever thread it came from - the UI's, a platform
 * callback's, a media button's - so the state below needs no lock and calls take effect in the order they were made.
 * Each start is a session of its own, and what a listener reports about an earlier session is ignored, since an
 * output that was just stopped may still be finishing a callback.
 */
internal class MetronomeEngine(
    private val output: AudioOutput,
    private val scope: CoroutineScope,
    dispatcher: CoroutineDispatcher,
    createSilentOutput: (CoroutineScope) -> AudioOutput = ::SilentAudioOutput,
) : Metronome {

    private val confined = dispatcher.limitedParallelism(1)
    private val silentOutput = createSilentOutput(scope)
    private val _playback = MutableStateFlow<MetronomePlayback>(MetronomePlayback.Stopped())
    override val playback = _playback.asStateFlow()
    private val _beats = MutableSharedFlow<MetronomeBeat>(extraBufferCapacity = BEAT_BUFFER, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val beats = _beats.asSharedFlow()
    private var activeOutput: AudioOutput? = null
    private var stream: ClickStream? = null
    private var session = 0
    private var releaseJob: Job? = null
    private var previewEndJob: Job? = null
    private var lastBarChangeId = 0

    /** The changes from the next bar not heard yet, oldest first: their ids, and the pattern each one brings. */
    private val pendingBarChanges = ArrayDeque<Pair<Int, MetronomePattern>>()

    override fun start(pattern: MetronomePattern) = onEngine {
        if (_playback.value is MetronomePlayback.Playing) {
            updatePlaying(pattern, fromNextBar = true)
            return@onEngine
        }
        closeOutput()
        val sessionId = ++session
        val listener = listenerFor(sessionId)
        val result = output.start(isPreview = false, createStream = streamFor(pattern), listener = listener)
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
                silentOutput.start(isPreview = false, createStream = streamFor(pattern), listener = listener)
                activeOutput = silentOutput
                MetronomePlayback.Playing(pattern, MetronomeAudioIssue.UNAVAILABLE)
            }
        }
        if (_playback.value is MetronomePlayback.Playing) releaseJob = releaseHeardBeats()
    }

    override fun update(pattern: MetronomePattern, fromNextBar: Boolean) = onEngine { updatePlaying(pattern, fromNextBar) }

    /**
     * A change from the next bar is [MetronomePlayback.Playing.pendingPattern] until its first click is heard, and a
     * change from the next beat made meanwhile replaces it there, since it waits with it. A second change from the next
     * bar made before the first is heard queues behind it, each becoming [MetronomePlayback.Playing.pattern] with its own
     * first heard click.
     */
    private fun updatePlaying(pattern: MetronomePattern, fromNextBar: Boolean) {
        val playing = _playback.value as? MetronomePlayback.Playing ?: return
        when {
            fromNextBar -> {
                val barChangeId = ++lastBarChangeId
                pendingBarChanges.addLast(barChangeId to pattern)
                stream?.update(pattern, barChangeId)
                _playback.value = playing.copy(pendingPattern = pattern)
            }
            playing.pendingPattern != null -> {
                if (playing.pendingPattern == pattern) return
                stream?.update(pattern, barChangeId = null)
                pendingBarChanges.lastOrNull()?.let { (id, _) -> pendingBarChanges[pendingBarChanges.lastIndex] = id to pattern }
                _playback.value = playing.copy(pendingPattern = pattern)
            }
            else -> {
                if (playing.pattern == pattern) return
                stream?.update(pattern, barChangeId = null)
                _playback.value = playing.copy(pattern = pattern)
            }
        }
    }

    override fun preview(sound: MetronomeSound, level: BeatLevel) = onEngine {
        if (_playback.value !is MetronomePlayback.Playing) {
            if (previewEndJob == null) {
                closeOutput()
                val result = output.start(
                    isPreview = true,
                    createStream = streamFor(pattern = null),
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

    /** Builds the stream an output pulls from for [pattern], or for previews alone where it is null, and keeps it. */
    private fun streamFor(pattern: MetronomePattern?): (Int) -> ClickStream = { sampleRate -> ClickStream(sampleRate, pattern).also { stream = it } }

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
     * calls back at a click's moment; a few milliseconds is far below what the eye notices in a flash. It polls closely
     * only near the next tick and once a chunk otherwise, since a click plays for minutes on a locked phone, where every
     * wake keeps a core out of its idle states.
     */
    private fun releaseHeardBeats() = scope.launch(confined) {
        val stream = stream ?: return@launch
        val output = activeOutput ?: return@launch
        val pending = ArrayDeque<MetronomeSequencer.Tick>()
        var hasReleased = false
        while (isActive) {
            while (true) pending.addLast(stream.renderedTicks.tryReceive().getOrNull() ?: break)
            val heardFrame = output.heardFrame()
            while (pending.isNotEmpty() && pending.first().frame <= heardFrame) {
                val tick = pending.removeFirst()
                val heardIndex = tick.barChangeId?.let { id -> pendingBarChanges.indexOfFirst { it.first == id } } ?: -1
                if (heardIndex >= 0) {
                    val heard = pendingBarChanges[heardIndex].second
                    repeat(heardIndex + 1) { pendingBarChanges.removeFirst() }
                    (_playback.value as? MetronomePlayback.Playing)?.let { playing ->
                        _playback.value = playing.copy(pattern = heard, pendingPattern = pendingBarChanges.lastOrNull()?.second)
                    }
                }
                _beats.tryEmit(MetronomeBeat(tick.beatIndex, tick.barIndex, tick.level, tick.isSubdivision))
                hasReleased = true
            }
            val remainingMillis = pending.firstOrNull()?.let { (it.frame - heardFrame) * 1_000 / stream.sampleRate }
            val wait = when {
                // Nothing heard yet (Android before its first timestamp answers -1), or nothing released yet this session
                // (the silent output hears frame 0 the moment it starts): poll closely.
                heardFrame < 0 || !hasReleased -> RELEASE_INTERVAL_MILLIS
                // A tick is rendered about a queue's depth (AudioOutput.QUEUED_SECONDS) before it is heard, so one chunk's
                // wait never misses one arriving.
                remainingMillis == null -> MAX_RELEASE_WAIT_MILLIS
                // Only Android's position moves continuously; the others move in steps of up to about a chunk, and a wait
                // computed straight to the tick could end just before the step that passes it and then wait a whole
                // chunk more. Near the tick the loop polls closely instead, so it is released within a poll of that step.
                remainingMillis <= NEAR_RELEASE_MILLIS -> RELEASE_INTERVAL_MILLIS
                else -> (remainingMillis - NEAR_RELEASE_MILLIS).coerceIn(1L, MAX_RELEASE_WAIT_MILLIS)
            }
            delay(wait)
        }
    }

    private fun closeOutput() {
        session++
        previewEndJob?.cancel()
        previewEndJob = null
        pendingBarChanges.clear()
        releaseJob?.cancel()
        releaseJob = null
        activeOutput?.stop()
        activeOutput = null
        stream = null
    }

    private companion object {
        const val BEAT_BUFFER = 16
        const val RELEASE_INTERVAL_MILLIS = 5L
        const val MAX_RELEASE_WAIT_MILLIS = (AudioOutput.CHUNK_SECONDS * 1000).toLong()
        const val NEAR_RELEASE_MILLIS = 50L
        const val PREVIEW_HOLD_MILLIS = 1_500L
    }
}
