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

import com.pandulapeter.campfire.data.source.remote.api.sync.SyncAuthorizationException
import com.pandulapeter.campfire.data.source.remote.api.sync.SyncScopeMissingException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** How a 401 is told apart: a token Dropbox refuses, or one it accepts but that lacks a permission. */
class DropboxTransportTest {

    @Test
    fun `a plain refusal renews the token once and then is a refusal of the credentials`() = runTest {
        var tokenRequestCount = 0
        val provider = dropboxProvider { request ->
            if (request.url.toString() == TOKEN_URL) {
                tokenRequestCount++
                respondJson("""{"access_token":"renewed","expires_in":14400}""")
            } else {
                respondJson("""{"error_summary":"invalid_access_token/...","error":{".tag":"invalid_access_token"}}""", HttpStatusCode.Unauthorized)
            }
        }
        assertFailsWith<SyncAuthorizationException> { provider.createSharedLink("abcdefghijklmnop") }
        assertEquals(expected = 1, actual = tokenRequestCount)
    }

    /** A renewed token carries the same permissions, and the files still sync, so the connection must stay as it is. */
    @Test
    fun `a missing permission is reported as such at once without renewing the token`() = runTest {
        var tokenRequestCount = 0
        val provider = dropboxProvider { request ->
            if (request.url.toString() == TOKEN_URL) {
                tokenRequestCount++
                respondJson("""{"access_token":"renewed","expires_in":14400}""")
            } else {
                respondJson(
                    """{"error":{".tag":"missing_scope","required_scope":"sharing.write"},"error_summary":"missing_scope/"}""",
                    HttpStatusCode.Unauthorized,
                )
            }
        }
        val exception = assertFailsWith<SyncScopeMissingException> { provider.createSharedLink("abcdefghijklmnop") }
        assertEquals(expected = "sharing.write", actual = exception.requiredScope)
        assertEquals(expected = 0, actual = tokenRequestCount)
    }
}
