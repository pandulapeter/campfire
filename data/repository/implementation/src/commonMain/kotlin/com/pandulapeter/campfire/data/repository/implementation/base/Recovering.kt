/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.repository.implementation.base

import com.pandulapeter.campfire.data.model.domain.Logger
import kotlinx.coroutines.CancellationException

/**
 * Runs [block] and answers its value, or - for any [Exception] but a cancellation - logs [describe]'s line and answers
 * [fallback]'s. A cancellation is rethrown, never logged, since a stopped coroutine is not a failure; a Throwable that
 * is not an Exception is not caught either. `inline`, so that every lambda may suspend where the caller can.
 *
 * Some callers log only `exception::class.simpleName` on purpose: the message of an exception that came from the
 * credentials document may quote a token.
 */
internal inline fun <T> Logger.recovering(
    describe: (Exception) -> String,
    fallback: (Exception) -> T,
    block: () -> T,
): T = try {
    block()
} catch (exception: CancellationException) {
    throw exception
} catch (exception: Exception) {
    log(describe(exception))
    fallback(exception)
}
