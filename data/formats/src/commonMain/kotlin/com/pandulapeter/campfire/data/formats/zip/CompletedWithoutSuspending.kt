/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.formats.zip

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/**
 * [block]'s result, for a block whose only suspensions are `yield`s and calls that return at once, on every platform
 * (the web has no `runBlocking`). In a context with neither a `Job` nor a dispatcher, `yield` returns without
 * suspending, so the block runs to its end before [startCoroutine] returns.
 *
 * A block that really suspends is an [AssertionError], not an `IllegalStateException`: the PDF reader catches that as a
 * damaged object, and a decoder that started to suspend would then read as damaged PDFs instead of failing. Nothing
 * resumes such a block afterwards, so it can neither hang nor hand back part of its result.
 */
internal fun <T> completedWithoutSuspending(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
    return (outcome ?: throw AssertionError("The block suspended.")).getOrThrow()
}
