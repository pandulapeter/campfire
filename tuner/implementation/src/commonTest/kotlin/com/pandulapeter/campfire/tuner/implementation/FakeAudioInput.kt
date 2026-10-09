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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * An input that records what the engine asks of it and hands out whatever window [signal] writes (false meaning no
 * window yet), at a position that moves on by a poll's worth of frames every [advanceEvery]th call unless [isFrozen].
 */
internal class FakeAudioInput : AudioInput {
    var result: AudioInputStart = AudioInputStart.Started(SAMPLE_RATE)

    /** When set, a start waits for it, as the web's does for the browser's answer. */
    var gate: CompletableDeferred<Unit>? = null

    /** Whether a start waiting at [gate] ignores being cancelled, as a platform call that cannot be interrupted does. */
    var isStartNonCancellable = false
    var signal: (FloatArray) -> Boolean = { window ->
        window.fill(0f)
        true
    }

    /** Whether the input has stopped delivering frames without saying so, handing out the same window again. */
    var isFrozen = false

    /** How many polls in a row see the same window, as they do between two of an iOS tap's buffers. */
    var advanceEvery = 1
    var listener: AudioInputListener? = null
        private set
    var startCount = 0
        private set
    var stopCount = 0
        private set

    override suspend fun start(listener: AudioInputListener): AudioInputStart {
        startCount++
        this.listener = listener
        gate?.let { gate -> if (isStartNonCancellable) withContext(NonCancellable) { gate.await() } else gate.await() }
        return result
    }

    private var position = 0L
    private var latestCount = 0

    override fun latest(window: FloatArray): Long {
        if (!signal(window)) return AudioInput.NO_WINDOW
        if (!isFrozen && latestCount++ % advanceEvery == 0) position += FRAMES_PER_POLL
        return position
    }

    override fun stop() {
        stopCount++
    }

    companion object {
        const val SAMPLE_RATE = 48_000

        /** 33 ms at [SAMPLE_RATE]. */
        private const val FRAMES_PER_POLL = 1_584L
    }
}
