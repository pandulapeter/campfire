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

import org.koin.core.annotation.Single

/**
 * An `AudioBufferSourceNode` looping the tone, through a gain node that ramps it in and out, in the context the input
 * uses too ([WebTunerAudio]). The loop is copied into its buffer a sample at a time, once per tone.
 */
@Single
internal class WebToneOutput : ToneOutput {

    private var isPlaying = false

    override fun play(render: (sampleRate: Int) -> ShortArray, onLost: () -> Unit): Boolean {
        stop()
        WebTunerAudio.install()
        val sampleRate = tunerSampleRate()
        val loop = render(sampleRate)
        createTunerToneBuffer(loop.size, sampleRate)
        loop.forEachIndexed { index, sample -> setTunerToneSample(index, sample / SHORT_SCALE) }
        startTunerTone(ToneOutput.FADE_SECONDS)
        isPlaying = true
        return true
    }

    override fun stop() {
        if (!isPlaying) return
        isPlaying = false
        stopTunerTone(ToneOutput.FADE_SECONDS)
    }

    private companion object {
        const val SHORT_SCALE = 32_768f
    }
}
