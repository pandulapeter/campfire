/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.implementation.network

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** What ends a call [withStallLimit] runs once nothing has been heard of it for the limit. */
internal class StalledException(limitMillis: Long) : Exception("Nothing arrived for $limitMillis ms.")

/**
 * Runs [block], ending it with a [StalledException] once [limitMillis] pass without it calling the `heard` function it
 * is handed: a bound on silence rather than on the whole call, so a large download over a slow line runs as long as data
 * keeps arriving. The engines' own socket timeout does this between packets, but the browser's `fetch` has none, and a
 * download without a total bound there would otherwise wait forever on a host that went quiet.
 *
 * The clock is the coroutine's, so a test runs it on virtual time. In a hidden browser tab, whose timers may run as
 * rarely as once a minute, the limit can only end a call later than asked, never sooner.
 */
internal suspend fun <T> withStallLimit(limitMillis: Long, block: suspend (heard: () -> Unit) -> T): T = coroutineScope {
    val heardCount = MutableStateFlow(0L)
    val watchdog = launch {
        heardCount.collectLatest {
            delay(limitMillis)
            throw StalledException(limitMillis)
        }
    }
    try {
        block { heardCount.value++ }
    } finally {
        watchdog.cancel()
    }
}
