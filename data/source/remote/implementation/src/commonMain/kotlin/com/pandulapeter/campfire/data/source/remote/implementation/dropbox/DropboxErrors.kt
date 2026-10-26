/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.implementation.dropbox

import com.pandulapeter.campfire.data.source.remote.api.sync.SyncScopeMissingException
import com.pandulapeter.campfire.data.source.remote.implementation.network.SyncHttpErrorMapper
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException

/** What Dropbox says beyond plain HTTP, read from the `error_summary` and `error` of its answers. */
internal object DropboxErrors : SyncHttpErrorMapper {

    override val serviceName = "Dropbox"

    /**
     * A 401 for a permission the connection was never granted (`missing_scope`): a renewed token carries the same
     * permissions, and the credentials are good for everything else, so it is thrown as [SyncScopeMissingException] at
     * once, never as a refusal that would end a run and show the connection as failed.
     */
    override suspend fun refusalBeyondRenewal(response: HttpResponse) = try {
        json.decodeFromString<DropboxScopeErrorResponse>(response.bodyAsText())
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        null
    }?.takeIf { it.errorSummary.startsWith("missing_scope") }?.let { answer ->
        SyncScopeMissingException("Dropbox needs a permission the connection lacks: ${answer.error.requiredScope}", answer.error.requiredScope)
    }

    /**
     * Several writes landing in one folder at once are answered with a 409 whose summary says
     * `too_many_write_operations`, which Dropbox documents as "back off and retry" rather than as a failure of that file.
     */
    override suspend fun asksToSlowDown(response: HttpResponse) =
        response.status == HttpStatusCode.Conflict && response.errorSummary().contains("too_many_write_operations")

    override suspend fun requestedWaitSeconds(response: HttpResponse) = try {
        json.decodeFromString<DropboxRateLimitResponse>(response.bodyAsText()).error.retryAfter
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        null
    }

    override suspend fun summaryOf(response: HttpResponse) = response.errorSummary()
}
