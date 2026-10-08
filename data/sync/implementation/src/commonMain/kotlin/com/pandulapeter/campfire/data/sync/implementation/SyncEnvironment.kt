/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(ExperimentalTime::class)

package com.pandulapeter.campfire.data.sync.implementation

import com.pandulapeter.campfire.data.model.domain.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

/**
 * What sync's long-lived collaborators run on - the same shape as the repositories' `RepositoryEnvironment`, which
 * this module cannot see - injected rather than built by each of them, so that their tests can run
 * every job they launch and every delay they wait on in the test's own virtual time.
 */
internal class SyncEnvironment(
    /** The parent of every scope [scopeFor] hands out: a SupervisorJob plus Dispatchers.Default in the app. */
    private val context: CoroutineContext,
    val timeSource: TimeSource.WithComparableMarks,
    val clock: Clock,
    /** Where a large document is encoded or decoded off the caller's thread. */
    val computation: CoroutineDispatcher,
    val logger: Logger,
) {

    /**
     * A scope of its own for one repository. Nothing launched there has anybody to throw to, and an exception that
     * leaves a job with no handler ends the process on Android and iOS: every job is written not to throw, and the
     * handler is there for the one that one day does, logging it as "A [name] job ended in an exception nothing caught".
     */
    fun scopeFor(name: String) = CoroutineScope(
        context + SupervisorJob(context[Job]) + CoroutineExceptionHandler { _, throwable ->
            logger.log("A $name job ended in an exception nothing caught: $throwable")
        },
    )
}
