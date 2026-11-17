/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
@ComponentScan
object PresentationModule {

    /**
     * Built here rather than declared on the class, since the scope is exactly what the tests replace with their own.
     * Nothing launched there has anybody to throw to, and an exception that leaves a job with no handler ends the process
     * on Android and iOS: every job is written not to throw, and the handler is there for the one that one day does.
     */
    @Single
    internal fun applicationScope(): ApplicationScope = ApplicationScope(
        CoroutineScope(
            SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, throwable ->
                println("A job of the app's own ended in an exception nothing caught: $throwable")
            },
        ),
    )
}
