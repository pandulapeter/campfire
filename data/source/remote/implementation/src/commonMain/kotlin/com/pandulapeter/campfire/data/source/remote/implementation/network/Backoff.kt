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

import kotlin.math.min

/**
 * What to wait, in seconds, before attempt [attempt] + 1 of a call to a service that asked to slow down without saying
 * for how long: two seconds, doubled on every attempt up to half a minute.
 */
internal fun exponentialBackoffSeconds(attempt: Int) = min(DEFAULT_RETRY_SECONDS shl attempt, MAXIMUM_RETRY_SECONDS)

private const val DEFAULT_RETRY_SECONDS = 2L
private const val MAXIMUM_RETRY_SECONDS = 32L
