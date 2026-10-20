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

import kotlin.math.roundToInt

/**
 * The tones as one stream of samples, for an output that fills its own buffers: every change is a ramp written into the
 * samples, never a jump. A tone fades in over [fadeFrames]; a new one fades in while the one it replaces fades out from
 * wherever it was, its own fade-in included; a stop fades out whatever sounds. A tone that has faded out is dropped only
 * at the end of a render, after it has written its silence, so nothing is ever cut while it can be heard.
 *
 * Not thread safe: the output guards it.
 */
internal class ToneStream(private val fadeFrames: Int) {

    private class Voice(val loop: ShortArray) {
        var position = 0
        var gain = 0f
        var isFadingOut = false
    }

    private val voices = mutableListOf<Voice>()

    /** Whether nothing sounds, or is still fading: what the next render writes is silence. */
    val isSilent get() = voices.isEmpty()

    /** Starts [loop], a buffer that loops without a seam (see [ToneSynthesizer]), replacing whatever sounds. */
    fun play(loop: ShortArray) {
        stop()
        voices += Voice(loop)
    }

    fun stop() = voices.forEach { it.isFadingOut = true }

    /** Writes the next [frames] samples into [output]. */
    fun render(output: ShortArray, frames: Int) {
        val step = 1f / fadeFrames
        for (frame in 0 until frames) {
            var value = 0f
            for (voice in voices) {
                voice.gain = if (voice.isFadingOut) (voice.gain - step).coerceAtLeast(0f) else (voice.gain + step).coerceAtMost(1f)
                value += voice.loop[voice.position] * voice.gain
                voice.position = (voice.position + 1) % voice.loop.size
            }
            output[frame] = value.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        voices.removeAll { it.isFadingOut && it.gain == 0f }
    }
}
