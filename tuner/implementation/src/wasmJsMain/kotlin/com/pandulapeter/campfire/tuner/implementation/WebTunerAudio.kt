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
        tuner.suspendIfIdle = function () {
            if (!tuner.stream && !tuner.tone && tuner.context && tuner.context.state === 'running') tuner.context.suspend().catch(function () {});
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

/** Resolves to `"ok"` once the microphone is open and connected, or to the name of the error it was refused with. */
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
                return 'ok';
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

internal fun tunerSample(index: Int): Float = js("window.__campfireTuner.samples[index]")

internal fun tunerSampleCount(): Int = js("window.__campfireTuner.samples.length")

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

internal fun startTunerTone(fadeSeconds: Double): Unit = js(
    """{
        var tuner = window.__campfireTuner;
        var context = tuner.context;
        var source = context.createBufferSource();
        source.buffer = tuner.toneBuffer;
        source.loop = true;
        var gain = context.createGain();
        gain.gain.setValueAtTime(0, context.currentTime);
        gain.gain.linearRampToValueAtTime(1, context.currentTime + fadeSeconds);
        source.connect(gain);
        gain.connect(context.destination);
        source.start();
        tuner.tone = { source: source, gain: gain };
    }"""
)

internal fun stopTunerTone(fadeSeconds: Double): Unit = js(
    """{
        var tuner = window.__campfireTuner;
        var tone = tuner.tone;
        if (!tone) return;
        tuner.tone = null;
        var now = tuner.context.currentTime;
        tone.gain.gain.cancelScheduledValues(now);
        tone.gain.gain.setValueAtTime(tone.gain.gain.value, now);
        tone.gain.gain.linearRampToValueAtTime(0, now + fadeSeconds);
        tone.source.stop(now + fadeSeconds * 2);
        setTimeout(function () { tuner.suspendIfIdle(); }, fadeSeconds * 4000);
    }"""
)
