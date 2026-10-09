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

import com.pandulapeter.campfire.tuner.api.Tuner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.annotation.Single
import kotlin.time.TimeSource

/**
 * The engine as Koin knows it: a singleton with a scope of its own. Everything it does is [TunerEngine]'s; this only
 * builds it with the real scope and dispatcher, and keeps the one constructor the Koin compiler plugin resolves.
 */
@Single
internal class TunerImpl(input: AudioInput, output: ToneOutput) : Tuner by TunerEngine(
    input = input,
    output = output,
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    dispatcher = Dispatchers.Default,
    timeSource = TimeSource.Monotonic,
)
