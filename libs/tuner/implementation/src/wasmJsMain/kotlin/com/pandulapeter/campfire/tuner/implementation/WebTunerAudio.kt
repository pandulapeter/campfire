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

import kotlin.js.Promise

/**
 * What the web input and the web tones share: one `AudioContext` of the tuner's own, kept on `window.__campfireTuner`,
 * and the capture-phase listener that resumes it on every press and key while a tuner screen shows, which is before
 * Compose sees the tap that wanted the sound. An idle context is suspended again by whichever of the two stops last,
 * or a few seconds after a press that started neither.
 */
internal object WebTunerAudio {

    fun install() = installTunerAudio()

    fun setGestureListening(isEnabled: Boolean) {
        install()
        if (isEnabled) armTunerGestures() else disarmTunerGestures()
    }
}

private fun installTunerAudio(): Unit = js(
    """{
        if (window.__campfireTuner) return;
        var tuner = window.__campfireTuner = { context: null, stream: null, source: null, analyser: null, samples: null, ended: false, tone: null, isArmed: false, generation: 0 };
        var AudioContextType = window.AudioContext || window.webkitAudioContext;
        tuner.ensureContext = function () {
            if (!AudioContextType) return null;
            try {
                if (!tuner.context) tuner.context = new AudioContextType({ latencyHint: 'interactive' });
                if (tuner.context.state !== 'running') tuner.context.resume().catch(function () {});
            } catch (error) {
                return null;
            }
            return tuner.context;
        };
        tuner.releaseUntil = 0;
        tuner.suspendIfIdle = function () {
            var context = tuner.context;
            if (tuner.stream || tuner.tone || !context || context.state !== 'running') return;
            // A fade still to be played, or still in the output's buffer, would be cut by the suspension: a pop.
            var remaining = tuner.releaseUntil + (context.outputLatency || 0) - context.currentTime;
            if (remaining > 0) {
                setTimeout(tuner.suspendIfIdle, remaining * 1000 + 50);
                return;
            }
            context.suspend().catch(function () {});
        };
        // The moment a change of the gain is scheduled for. currentTime is what the audio thread had rendered when the
        // page last heard from it, and it renders a whole device buffer per callback, so by the time a ramp starting at
        // currentTime reaches it, the ramp can be partly or wholly in the past and is applied as a step. Scheduling at
        // least a callback's length plus two render quanta ahead keeps every fade whole.
        tuner.scheduleTime = function () {
            var context = tuner.context;
            return context.currentTime + Math.max(context.baseLatency || 0, 0.01) + 256 / context.sampleRate;
        };
        tuner.fadeOut = function (tone, at, fadeSeconds) {
            var gain = tone.gain.gain;
            // Held where the fade-in has got to at that moment, to the sample. Firefox has no cancelAndHoldAtTime, and
            // there the ramp is cut at the same point by ending a ramp of its own there: from the fade-in's start while
            // it is still rising, else level from its end.
            if (gain.cancelAndHoldAtTime) {
                gain.cancelAndHoldAtTime(at);
            } else {
                gain.cancelScheduledValues(at);
                gain.linearRampToValueAtTime(Math.min(1, Math.max(0, (at - tone.startAt) / fadeSeconds)), at);
            }
            gain.linearRampToValueAtTime(0, at + fadeSeconds);
            tone.source.stop(at + fadeSeconds * 2);
            tuner.releaseUntil = Math.max(tuner.releaseUntil, at + fadeSeconds * 2);
        };
        tuner.idleTimeout = 0;
        tuner.onGesture = function () {
            tuner.ensureContext();
            clearTimeout(tuner.idleTimeout);
            tuner.idleTimeout = setTimeout(tuner.suspendIfIdle, 3000);
        };
    }"""
)

private fun armTunerGestures(): Unit = js(
    """{
        var tuner = window.__campfireTuner;
        if (tuner.isArmed) return;
        tuner.isArmed = true;
        ['pointerdown', 'pointerup', 'touchend', 'keydown'].forEach(function (type) { window.addEventListener(type, tuner.onGesture, true); });
    }"""
)

private fun disarmTunerGestures(): Unit = js(
    """{
        var tuner = window.__campfireTuner;
        if (!tuner.isArmed) return;
        tuner.isArmed = false;
        ['pointerdown', 'pointerup', 'touchend', 'keydown'].forEach(function (type) { window.removeEventListener(type, tuner.onGesture, true); });
    }"""
)

/**
 * Resolves to `"ok"` once the microphone is open and connected and the context running (or given up waiting for), or to
 * the name of the error it was refused with.
 */
internal fun openTunerMicrophone(): Promise<JsString> = js(
    """(function () {
        var tuner = window.__campfireTuner;
        // An answer that arrives after the microphone was closed again belongs to nobody, and its tracks are ended at once,
        // or the browser's recording indicator would stay on for a stream nothing reads.
        var generation = tuner.generation;
        if (!window.isSecureContext || !navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) return Promise.resolve('NotSupportedError');
        return navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: false, noiseSuppression: false, autoGainControl: false } }).then(function (stream) {
            var stopTracks = function () { stream.getTracks().forEach(function (track) { track.stop(); }); };
            if (generation !== tuner.generation) {
                stopTracks();
                return 'AbortError';
            }
            var source = null;
            try {
                var context = tuner.ensureContext();
                if (!context) {
                    stopTracks();
                    return 'NotSupportedError';
                }
                source = context.createMediaStreamSource(stream);
                var analyser = context.createAnalyser();
                source.connect(analyser);
                stream.getAudioTracks().forEach(function (track) { track.addEventListener('ended', function () { if (tuner.stream === stream) tuner.ended = true; }); });
                tuner.ended = false;
                tuner.source = source;
                tuner.analyser = analyser;
                tuner.stream = stream;
                // A context made or resumed just now reaches 'running' a few frames later even where the page may play,
                // and an answer read before then would show the waiting-for-a-tap line only to take it back. Where no
                // gesture allows it, resume() never settles, so the wait is cut short and the line shows after all.
                if (context.state === 'running') return 'ok';
                return Promise.race([
                    context.resume().catch(function () {}),
                    new Promise(function (resolve) { setTimeout(resolve, 500); }),
                ]).then(function () {
                    // The microphone was closed while it waited, which has already ended the stream's tracks.
                    return generation === tuner.generation ? 'ok' : 'AbortError';
                });
            } catch (error) {
                // A graph that could not be built must not leave the browser's recording indicator on for a stream nothing reads.
                if (source) { try { source.disconnect(); } catch (ignored) {} }
                stopTracks();
                return (error && error.name) || 'Error';
            }
        }, function (error) {
            return (error && error.name) || 'Error';
        });
    })()"""
)

/**
 * Sizes the analyser to hold the input the detector reads, in the power of two an `fftSize` has to be, and answers the
 * context's sample rate.
 */
internal fun prepareTunerAnalyser(inputSize: Int): Int = js(
    """{
        var tuner = window.__campfireTuner;
        var size = 32;
        while (size < inputSize) size *= 2;
        tuner.analyser.fftSize = size;
        tuner.samples = new Float32Array(size);
        return tuner.context.sampleRate;
    }"""
)

internal fun tunerSampleRate(): Int = js("(window.__campfireTuner.ensureContext() || { sampleRate: 48000 }).sampleRate")

/** Copies the analyser's latest window into its buffer; false once there is no analyser. */
internal fun readTunerWindow(): Boolean = js(
    """{
        var tuner = window.__campfireTuner;
        if (!tuner.analyser) return false;
        tuner.analyser.getFloatTimeDomainData(tuner.samples);
        return true;
    }"""
)

/** The frames the tuner's context has rendered, which stand still while it is suspended. */
internal fun tunerFramePosition(): Double = js(
    "window.__campfireTuner.context ? Math.round(window.__campfireTuner.context.currentTime * window.__campfireTuner.context.sampleRate) : -1"
)

/**
 * The analyser's buffer, which [readTunerWindow] has just filled, handed over once per poll so that each sample is read
 * by [tunerSampleOf] as one indexed read of its argument rather than a walk down from the global object.
 */
internal fun tunerSamples(): JsAny = js("window.__campfireTuner.samples")

internal fun tunerSampleCountOf(samples: JsAny): Int = js("samples.length")

internal fun tunerSampleOf(samples: JsAny, index: Int): Float = js("samples[index]")

internal fun isTunerInputEnded(): Boolean = js("window.__campfireTuner.ended")

internal fun isTunerContextRunning(): Boolean = js("!!window.__campfireTuner.context && window.__campfireTuner.context.state === 'running'")

internal fun closeTunerMicrophone(): Unit = js(
    """{
        var tuner = window.__campfireTuner;
        tuner.generation++;
        if (tuner.stream) tuner.stream.getTracks().forEach(function (track) { track.stop(); });
        if (tuner.source) tuner.source.disconnect();
        tuner.stream = null;
        tuner.source = null;
        tuner.analyser = null;
        tuner.ended = false;
        tuner.suspendIfIdle();
    }"""
)

internal fun createTunerToneBuffer(length: Int, sampleRate: Int): Unit = js(
    """{
        var tuner = window.__campfireTuner;
        tuner.toneBuffer = tuner.context.createBuffer(1, length, sampleRate);
        tuner.toneChannel = tuner.toneBuffer.getChannelData(0);
    }"""
)

internal fun setTunerToneSample(index: Int, sample: Float): Unit = js("{ window.__campfireTuner.toneChannel[index] = sample; }")

/** Starts the loop in [createTunerToneBuffer], crossfading from the tone playing, if any, at the same moment. */
internal fun startTunerTone(fadeSeconds: Double): Unit = js(
    """{
        var tuner = window.__campfireTuner;
        var context = tuner.context;
        var at = tuner.scheduleTime();
        if (tuner.tone) tuner.fadeOut(tuner.tone, at, fadeSeconds);
        var source = context.createBufferSource();
        source.buffer = tuner.toneBuffer;
        source.loop = true;
        var gain = context.createGain();
        gain.gain.setValueAtTime(0, at);
        gain.gain.linearRampToValueAtTime(1, at + fadeSeconds);
        source.connect(gain);
        gain.connect(context.destination);
        source.start(at);
        tuner.tone = { source: source, gain: gain, startAt: at };
    }"""
)

internal fun stopTunerTone(fadeSeconds: Double): Unit = js(
    """{
        var tuner = window.__campfireTuner;
        var tone = tuner.tone;
        if (!tone) return;
        tuner.tone = null;
        tuner.fadeOut(tone, tuner.scheduleTime(), fadeSeconds);
        tuner.suspendIfIdle();
    }"""
)
