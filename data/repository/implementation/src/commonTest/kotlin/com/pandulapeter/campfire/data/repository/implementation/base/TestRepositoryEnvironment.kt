/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)

package com.pandulapeter.campfire.data.repository.implementation.base

import com.pandulapeter.campfire.data.model.domain.Logger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** The moment [testEnvironment]'s clock always answers, far enough from zero to count as a real time. */
internal val TEST_NOW: Instant = Instant.fromEpochMilliseconds(1_760_000_000_000)

/**
 * Runs a repository on the test's scheduler: whatever it launches is cancelled with [TestScope.backgroundScope] at the
 * end of the test, and every delay and time mark it takes is virtual.
 */
internal fun TestScope.testEnvironment(
    clock: Clock = FixedClock(TEST_NOW),
    logger: Logger = Logger.Standard,
) = RepositoryEnvironment(
    context = backgroundScope.coroutineContext,
    timeSource = testScheduler.timeSource,
    clock = clock,
    computation = StandardTestDispatcher(testScheduler),
    logger = logger,
)

private class FixedClock(private val now: Instant) : Clock {
    override fun now() = now
}
