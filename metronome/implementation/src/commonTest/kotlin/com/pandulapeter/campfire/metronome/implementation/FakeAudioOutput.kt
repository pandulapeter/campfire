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

/** An output that plays nothing and records what the engine asks of it, its [heardFrame] set by the test. */
internal class FakeAudioOutput(var result: AudioOutputStart = AudioOutputStart.Started()) : AudioOutput {

    val starts = mutableListOf<Boolean>()
    var stopCount = 0
        private set
    var stream: ClickStream? = null
        private set
    var listener: AudioOutputListener? = null
        private set
    var heardFrame = -1L

    override fun start(isPreview: Boolean, createStream: (sampleRate: Int) -> ClickStream, listener: AudioOutputListener): AudioOutputStart {
        starts += isPreview
        stream = createStream(SAMPLE_RATE)
        this.listener = listener
        return result
    }

    override fun heardFrame() = heardFrame

    override fun stop() {
        stopCount++
    }

    companion object {
        const val SAMPLE_RATE = 48_000
    }
}
