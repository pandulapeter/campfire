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

/**
 * The platform's speaker for the reference tones, one annotated class per platform source set: a buffer
 * [ToneSynthesizer] wrote to loop seamlessly, played round and round with a fade of a few milliseconds at both ends so
 * that neither clicks. A steady tone needs no feed thread, no heard frame and no background playback, which is why
 * this is not the metronome's output.
 */
internal interface ToneOutput {

    /**
     * Plays the loop [render] writes for the output's own sample rate until [stop], replacing one already playing.
     * Returns whether it plays; [onLost] is called from any thread if the platform takes the sound away (audio focus).
     */
    fun play(render: (sampleRate: Int) -> ShortArray, onLost: () -> Unit): Boolean

    /** Fades out and stops. Does nothing when nothing plays. */
    fun stop()

    companion object {
        const val FADE_SECONDS = 0.01
        const val DEFAULT_SAMPLE_RATE = 48_000
    }
}
