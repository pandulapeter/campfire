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

import kotlinx.coroutines.CoroutineScope

/**
 * The scope of work that belongs to the app rather than to a screen, as a sync run does: what is launched here outlives
 * the view model, which Android clears when its activity finishes while the process goes on. Built by
 * [PresentationModule] on the main thread's dispatcher, as the view model's own scope is, so that what its holders share
 * with the view model is touched on one thread; tests pass their own.
 *
 * Public because the public view model's constructor takes a holder built on it.
 */
class ApplicationScope internal constructor(scope: CoroutineScope) : CoroutineScope by scope
