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

import io.ktor.client.statement.HttpResponse

/**
 * What a sync service says beyond plain HTTP and OAuth, for [SyncHttpTransport] and
 * [com.pandulapeter.campfire.data.source.remote.implementation.auth.OAuthTokens]. Each reads an answer the transport
 * keeps in memory, so a body may be read by several of them.
 */
internal interface SyncHttpErrorMapper {

    /** The service's name, in the messages of the failures thrown for it. */
    val serviceName: String

    /**
     * The failure a 401 is at once, without a renewed token being tried, or null for a refusal a renewal may cure. A
     * permission the connection was never granted is the case: a renewed token carries the same permissions.
     */
    suspend fun refusalBeyondRenewal(response: HttpResponse): Exception?

    /** Whether an answer other than a 408, a 429, or a 5xx asks to be sent again later. */
    suspend fun asksToSlowDown(response: HttpResponse): Boolean

    /** A wait the body of an answer asks for, in seconds, which takes precedence over `Retry-After`. */
    suspend fun requestedWaitSeconds(response: HttpResponse): Long?

    /** The service's own account of a failed answer, for a token endpoint refusal that names no OAuth error. */
    suspend fun summaryOf(response: HttpResponse): String
}
