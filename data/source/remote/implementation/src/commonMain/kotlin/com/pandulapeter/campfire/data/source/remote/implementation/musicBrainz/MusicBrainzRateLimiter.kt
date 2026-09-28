/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.implementation.musicBrainz

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Spaces the app's requests to MusicBrainz at least [interval] apart, however many searches ask at once. MusicBrainz
 * allows one request a second on average from one address and refuses every request while a client goes faster, so
 * the one instance there is holds the whole app to it. Only the start of a request is spaced, not its length: a slow
 * answer is not a reason to wait longer before the next question.
 */
internal class MusicBrainzRateLimiter(
    private val timeSource: TimeSource.WithComparableMarks,
    private val interval: Duration,
) {
    private val mutex = Mutex()
    private var lastStart: ComparableTimeMark? = null

    /** Suspends until a request may start, and counts it as started. */
    suspend fun awaitTurn() = mutex.withLock {
        lastStart?.let { last -> delay(interval - last.elapsedNow()) }
        lastStart = timeSource.markNow()
    }
}
