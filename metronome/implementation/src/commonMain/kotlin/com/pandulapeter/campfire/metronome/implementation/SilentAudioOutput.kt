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

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * What a click runs on where no output can be opened: the same stream and the same sequencer, clocked by
 * [timeSource] (the monotonic one outside tests), with nothing played. The UI therefore has one source of beats whether or not there is sound.
 */
internal class SilentAudioOutput(
    private val scope: CoroutineScope,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : AudioOutput {

    private var job: Job? = null
    private var startMark: TimeMark? = null

    override fun start(isPreview: Boolean, createStream: (sampleRate: Int) -> ClickStream, listener: AudioOutputListener): AudioOutputStart {
        stop()
        val stream = createStream(SAMPLE_RATE)
        val mark = timeSource.markNow().also { startMark = it }
        val aheadFrames = (AudioOutput.QUEUED_SECONDS * SAMPLE_RATE).toLong()
        job = scope.launch(dispatcher) {
            while (isActive) {
                val now = mark.elapsedNow().inWholeMicroseconds * SAMPLE_RATE / 1_000_000
                stream.schedule(nowFrame = now, untilFrame = now + aheadFrames) { _, _, _, _ -> }
                delay((AudioOutput.CHUNK_SECONDS * 1000).toLong())
            }
        }
        return AudioOutputStart.Started()
    }

    override fun heardFrame() = startMark?.elapsedNow()?.inWholeMicroseconds?.let { it * SAMPLE_RATE / 1_000_000 } ?: -1L

    override fun stop() {
        job?.cancel()
        job = null
        startMark = null
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
    }
}
