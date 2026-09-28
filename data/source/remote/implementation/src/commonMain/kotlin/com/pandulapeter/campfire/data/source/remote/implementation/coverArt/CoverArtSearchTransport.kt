/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.implementation.coverArt

import com.pandulapeter.campfire.data.source.remote.api.CoverArtSearchException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException

/**
 * Runs [block], the part of a cover search that talks to [serviceName], turning anything the transport throws into a
 * [CoverArtSearchException], the way the Dropbox provider reads it: the browser engine reports a failed `fetch` as a
 * `kotlin.Error`, so every `Throwable` that is not a cancellation of this coroutine counts.
 */
internal suspend fun <T> coverArtSearchTransport(serviceName: String, block: suspend () -> T): T = try {
    block()
} catch (exception: CancellationException) {
    currentCoroutineContext().ensureActive()
    throw CoverArtSearchException(exception.message ?: "$serviceName could not be reached.", exception)
} catch (exception: CoverArtSearchException) {
    throw exception
} catch (throwable: Throwable) {
    throw CoverArtSearchException(throwable.message ?: "$serviceName could not be reached.", throwable)
}

/**
 * Reads an answer of [serviceName] with [parse] away from the caller's thread, which is the main one: a search result
 * lists every release of every recording it found and runs to hundreds of kilobytes, and decoding that on the main
 * thread lands on the frames of the sheet sliding up. Anything that does not decode is the service answering with
 * something that is not a search result.
 */
internal suspend fun <T> parseCoverArtSearchAnswer(serviceName: String, parse: () -> T): T = withContext(Dispatchers.Default) {
    try {
        parse()
    } catch (exception: SerializationException) {
        throw CoverArtSearchException("$serviceName answered with something that is not a search result.", exception)
    } catch (exception: IllegalArgumentException) {
        throw CoverArtSearchException("$serviceName answered with something that is not a search result.", exception)
    }
}
