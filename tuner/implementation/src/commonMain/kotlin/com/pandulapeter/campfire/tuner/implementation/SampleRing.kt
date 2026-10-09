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

import kotlin.concurrent.Volatile

/**
 * The input's latest frames, written by the platform's capture thread and copied out by the engine's poll; the three
 * platforms that are handed chunks keep one, the web has its own in the analyser.
 *
 * One writer and one reader, without a lock: the writer stores the samples before it publishes how many there are, and
 * the ring holds [capacity] frames - several windows - so the window the reader copies is never written over while it
 * is being copied, which takes microseconds against the tens of milliseconds a chunk of capture is.
 */
internal class SampleRing(private val capacity: Int) {

    private val samples = FloatArray(capacity)

    @Volatile
    private var written = 0L

    /** Starts over, for a new session. Only while nothing writes. */
    fun clear() {
        written = 0L
    }

    /** Appends [count] 16-bit samples of [source]. */
    fun write(source: ShortArray, count: Int) {
        val start = written
        for (index in 0 until count) samples[((start + index) % capacity).toInt()] = source[index] / SHORT_SCALE
        written = start + count
    }

    /** Appends [count] samples of [source], already full scale ±1. */
    fun write(source: FloatArray, count: Int) {
        val start = written
        for (index in 0 until count) samples[((start + index) % capacity).toInt()] = source[index]
        written = start + count
    }

    /** Copies the latest `window.size` samples into [window], and answers whether that many have been written yet. */
    fun latest(window: FloatArray): Boolean {
        val end = written
        if (end < window.size) return false
        val start = end - window.size
        for (index in window.indices) window[index] = samples[((start + index) % capacity).toInt()]
        return true
    }

    companion object {
        /** Four of the largest window the detector reads, 8192 frames at 96 kHz. */
        const val CAPACITY = 32_768
        private const val SHORT_SCALE = 32_768f
    }
}
