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

package com.pandulapeter.campfire.metronome.implementation

import com.pandulapeter.campfire.metronome.api.model.MetronomeAudioIssue
import com.pandulapeter.campfire.metronome.api.model.MetronomeSound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import kotlin.js.Promise

/**
 * Web Audio with the usual two clocks: every click due within the next [AudioOutput.QUEUED_SECONDS] is scheduled on
 * the `AudioContext`'s own clock with `AudioBufferSourceNode.start(time)`, and the scheduling is woken every 25 ms by
 * `metronome-timer.js` (in `:app:web`'s resources), a dedicated worker - a hidden tab's own timers are throttled to
 * once a second, a worker's are not, so the click survives a tab switch. A Kotlin lambda cannot be handed to a
 * `js(...)` block, so the wake-ups come back as one promise each, awaited in a loop.
 *
 * A page may only start its audio inside a user gesture, and Compose handles a click after the DOM event that caused
 * it has returned, so a capture-phase listener resumes a suspended context on every press and key (creating it on the
 * first), which happens before Compose sees the tap that starts the click. Until the context runs the click reports
 * [MetronomeAudioIssue.WAITING_FOR_GESTURE]. An idle context is suspended again shortly after, so that a page nobody
 * clicks a metronome on keeps no audio device open.
 */
@Single
internal class WebAudioOutput : AudioOutput {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var sampleRate = 0
    private var startTime = 0.0

    init {
        installAudio()
    }

    override fun start(isPreview: Boolean, createStream: (sampleRate: Int) -> ClickStream, listener: AudioOutputListener): AudioOutputStart {
        stop()
        if (!openContext()) return AudioOutputStart.Unavailable
        sampleRate = contextSampleRate()
        // A little later than now, so that the first click is not scheduled in the past by the time it reaches the
        // audio thread.
        startTime = contextTime() + START_DELAY_SECONDS
        val stream = createStream(sampleRate)
        val aheadFrames = (AudioOutput.QUEUED_SECONDS * sampleRate).toLong()
        val loadedVoices = mutableSetOf<String>()
        var isRunning = isContextRunning()
        job = scope.launch {
            while (isActive) {
                val nowFrame = ((contextTime() - startTime) * sampleRate).toLong()
                stream.schedule(nowFrame = nowFrame, untilFrame = nowFrame + aheadFrames) { frame, sound, voice, gain ->
                    val key = voiceKey(sound, voice)
                    if (loadedVoices.add(key)) loadVoice(key, stream.samplesOf(sound, voice))
                    playVoice(key, maxOf(startTime + frame.toDouble() / sampleRate, contextTime()), gain.toDouble())
                }
                isContextRunning().let { running ->
                    if (running != isRunning) {
                        isRunning = running
                        listener.onAudioIssueChanged(if (running) null else MetronomeAudioIssue.WAITING_FOR_GESTURE)
                    }
                }
                awaitTimerTick().await<JsAny?>()
            }
        }
        return AudioOutputStart.Started(if (isRunning) null else MetronomeAudioIssue.WAITING_FOR_GESTURE)
    }

    override fun heardFrame() = if (job == null) -1L else ((contextTime() - contextOutputLatency() - startTime) * sampleRate).toLong()

    override fun stop() {
        job?.cancel() ?: return
        job = null
        closeContext()
    }

    private fun voiceKey(sound: MetronomeSound, voice: ClickVoice) = "${sound.id}-${voice.name}"

    /**
     * Copies a voice into an `AudioBuffer` a sample at a time: one crossing per sample, done once per voice, is far
     * cheaper than any of the ways of handing a whole array over.
     */
    private fun loadVoice(key: String, samples: FloatArray) {
        createVoiceBuffer(key, samples.size)
        samples.forEachIndexed { index, sample -> setVoiceSample(key, index, sample.toDouble()) }
    }

    private companion object {
        const val START_DELAY_SECONDS = 0.05
    }
}

private fun installAudio(): Unit = js(
    """{
        if (window.__campfireMetronome) return;
        var metronome = window.__campfireMetronome = { context: null, buffers: {}, sources: new Set(), isActive: false, timer: null, waiting: null, idleTimeout: 0 };
        var AudioContextType = window.AudioContext || window.webkitAudioContext;
        function onGesture() {
            if (!AudioContextType) return;
            try {
                if (!metronome.context) metronome.context = new AudioContextType({ latencyHint: 'playback' });
                if (metronome.context.state !== 'running') metronome.context.resume().catch(function () {});
            } catch (error) {
                return;
            }
            clearTimeout(metronome.idleTimeout);
            metronome.idleTimeout = setTimeout(function () {
                if (!metronome.isActive && metronome.context && metronome.context.state === 'running') metronome.context.suspend().catch(function () {});
            }, 3000);
        }
        ['pointerdown', 'pointerup', 'touchend', 'keydown'].forEach(function (type) { window.addEventListener(type, onGesture, true); });
    }"""
)

private fun openContext(): Boolean = js(
    """{
        var metronome = window.__campfireMetronome;
        var AudioContextType = window.AudioContext || window.webkitAudioContext;
        if (!AudioContextType) return false;
        try {
            if (!metronome.context) metronome.context = new AudioContextType({ latencyHint: 'playback' });
            if (metronome.context.state !== 'running') metronome.context.resume().catch(function () {});
        } catch (error) {
            return false;
        }
        metronome.isActive = true;
        if (!metronome.timer) {
            metronome.timer = new Worker(window.campfireVersioned ? window.campfireVersioned('metronome-timer.js') : 'metronome-timer.js');
            metronome.timer.onmessage = function () {
                var waiting = metronome.waiting;
                metronome.waiting = null;
                if (waiting) waiting();
            };
        }
        metronome.timer.postMessage('start');
        return true;
    }"""
)

private fun closeContext(): Unit = js(
    """{
        var metronome = window.__campfireMetronome;
        metronome.isActive = false;
        metronome.sources.forEach(function (source) { try { source.stop(); } catch (error) {} });
        metronome.sources.clear();
        if (metronome.timer) metronome.timer.postMessage('stop');
        var waiting = metronome.waiting;
        metronome.waiting = null;
        if (waiting) waiting();
        if (metronome.context && metronome.context.state === 'running') metronome.context.suspend().catch(function () {});
    }"""
)

private fun awaitTimerTick(): Promise<JsAny?> = js("new Promise(function (resolve) { window.__campfireMetronome.waiting = resolve; })")

private fun contextSampleRate(): Int = js("window.__campfireMetronome.context.sampleRate")

private fun contextTime(): Double = js("window.__campfireMetronome.context.currentTime")

private fun contextOutputLatency(): Double = js("window.__campfireMetronome.context.outputLatency || 0")

private fun isContextRunning(): Boolean = js("window.__campfireMetronome.context.state === 'running'")

private fun createVoiceBuffer(key: String, length: Int): Unit = js(
    """{
        var context = window.__campfireMetronome.context;
        window.__campfireMetronome.buffers[key] = context.createBuffer(1, length, context.sampleRate);
    }"""
)

private fun setVoiceSample(key: String, index: Int, sample: Double): Unit = js("{ window.__campfireMetronome.buffers[key].getChannelData(0)[index] = sample; }")

private fun playVoice(key: String, time: Double, gain: Double): Unit = js(
    """{
        var metronome = window.__campfireMetronome;
        var source = metronome.context.createBufferSource();
        source.buffer = metronome.buffers[key];
        var gainNode = metronome.context.createGain();
        gainNode.gain.value = gain;
        source.connect(gainNode);
        gainNode.connect(metronome.context.destination);
        metronome.sources.add(source);
        source.onended = function () { metronome.sources.delete(source); };
        source.start(time);
    }"""
)
