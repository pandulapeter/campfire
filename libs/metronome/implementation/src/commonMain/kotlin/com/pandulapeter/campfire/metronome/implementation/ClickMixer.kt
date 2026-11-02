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

import com.pandulapeter.campfire.metronome.api.model.MetronomeSound

/**
 * Mixes clicks into 16-bit PCM a chunk at a time, carrying a click that does not end within one chunk into the next,
 * so that the output is the same however it is chunked. Every buffer is reused, so a chunk allocates nothing once the
 * voices have been rendered - which matters on Kotlin/Native, where a collection on the feeding thread is a gap in the
 * sound.
 */
internal class ClickMixer(private val sampleRate: Int) {

    private val voices = HashMap<Pair<MetronomeSound, ClickVoice>, FloatArray>()
    private val playing = ArrayList<PlayingClick>()
    private var mixBuffer = FloatArray(0)

    /** The frame the next rendered sample is. */
    var position = 0L
        private set

    fun samplesOf(sound: MetronomeSound, voice: ClickVoice) = voices.getOrPut(sound to voice) {
        ClickSynthesizer.render(sound, voice, sampleRate)
    }

    /** Adds a click that starts at [frame], which a click due before [position] is moved up to. */
    fun add(frame: Long, sound: MetronomeSound, voice: ClickVoice, gain: Float) {
        playing += PlayingClick(samplesOf(sound, voice), maxOf(frame, position), gain)
    }

    fun render(output: ShortArray, frames: Int) {
        if (mixBuffer.size < frames) mixBuffer = FloatArray(frames)
        mixBuffer.fill(0f, 0, frames)
        val end = position + frames
        val iterator = playing.iterator()
        while (iterator.hasNext()) {
            val click = iterator.next()
            if (click.startFrame >= end) continue
            val firstOutput = (click.startFrame - position).coerceAtLeast(0).toInt()
            val firstSample = (position - click.startFrame).coerceAtLeast(0).toInt()
            val count = minOf(frames - firstOutput, click.samples.size - firstSample)
            for (index in 0 until count) {
                mixBuffer[firstOutput + index] += click.samples[firstSample + index] * click.gain
            }
            if (click.startFrame + click.samples.size <= end) iterator.remove()
        }
        for (index in 0 until frames) {
            output[index] = (mixBuffer[index].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        }
        position = end
    }

    private class PlayingClick(
        val samples: FloatArray,
        val startFrame: Long,
        val gain: Float,
    )
}
