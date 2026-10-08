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

import com.pandulapeter.campfire.metronome.api.Metronome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.annotation.Single

/**
 * The engine as Koin knows it: a singleton with a scope of its own, as the sync repository is, so that a click belongs
 * to the app. Everything it does is [MetronomeEngine]'s; this only builds it with the real scope and dispatcher, and
 * keeps the one constructor the Koin compiler plugin resolves.
 */
@Single
internal class MetronomeImpl(output: AudioOutput) : Metronome by MetronomeEngine(
    output = output,
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    dispatcher = Dispatchers.Default,
)
