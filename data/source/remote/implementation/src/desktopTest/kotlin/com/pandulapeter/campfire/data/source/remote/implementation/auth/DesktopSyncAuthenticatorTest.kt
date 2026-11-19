/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.implementation.auth

import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.sync.AuthorizationCompletionPage
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The desktop listens on the port its configuration names, whichever app or service it is configured for. */
class DesktopSyncAuthenticatorTest {

    @Test
    fun `listens on the configured port`() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val authenticator = DesktopSyncAuthenticator(
            systemBrowser = { false },
            logger = Logger.Standard,
            redirect = OAuthRedirectConfiguration(scheme = "notes", host = "signin", desktopPort = port),
        )
        assertEquals(expected = "http://127.0.0.1:$port", actual = authenticator.prepareRedirectUri())
        assertTrue(runCatching { Socket("127.0.0.1", port).close() }.isSuccess, "Nothing listens on the configured port.")
        // A browser that cannot be opened ends the authorization at once, which releases the port.
        authenticator.authorize("https://example.com/authorize", AuthorizationCompletionPage(title = "Done", message = "Close this tab."))
        Unit
    }
}
