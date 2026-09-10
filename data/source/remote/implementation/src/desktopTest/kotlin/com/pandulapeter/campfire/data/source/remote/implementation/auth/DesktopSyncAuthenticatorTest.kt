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

import com.pandulapeter.campfire.data.source.remote.api.SyncAuthenticator
import com.pandulapeter.campfire.data.source.remote.api.model.AuthorizationCompletionPage
import java.io.PrintWriter
import java.net.ConnectException
import java.net.Socket
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The desktop's loopback listener, which is the one authenticator whose behaviour is not a straight line: `accept`
 * blocks a thread and notices neither a cancelled coroutine nor an interrupt, so giving up on an authorization has
 * to close the socket underneath it. Getting that wrong leaves the port held and the user unable to try again, and
 * it is invisible until somebody actually cancels - which is what these cover.
 *
 * No browser is opened: the launcher is injected, which is the only reason this seam exists.
 */
class DesktopSyncAuthenticatorTest {

    @Test
    fun `prepares a loopback redirect uri on the registered port`() = runBlocking {
        val authenticator = DesktopSyncAuthenticator { }
        try {
            assertEquals(expected = "http://127.0.0.1:53682", actual = authenticator.prepareRedirectUri())
            assertTrue(isPortOpen(), "The socket the service redirects to is not listening.")
        } finally {
            authenticator.close()
        }
    }

    /** The bug this exists for: cancelling used to leave the thread blocked and the port held for five minutes. */
    @Test
    fun `cancelling the authorization releases the port`() = runBlocking {
        val authenticator = DesktopSyncAuthenticator { }
        authenticator.prepareRedirectUri()
        val authorization = async { authenticator.authorize("https://example.com/authorize", COMPLETION_PAGE) }
        // Long enough for `accept` to actually be blocking, which is the state the cancellation has to reach.
        delay(300)
        assertTrue(isPortOpen(), "The socket should still be listening while the authorization waits.")

        authorization.cancel()
        withTimeout(5_000) {
            while (isPortOpen()) {
                delay(50)
            }
        }
        assertFalse(isPortOpen(), "The port is still held after the authorization was cancelled.")
    }

    /** And the ordinary path still works: the browser's request comes back as the redirect it carries. */
    @Test
    fun `a redirect delivered to the socket is returned`() = runBlocking {
        val authenticator = DesktopSyncAuthenticator { }
        authenticator.prepareRedirectUri()
        val authorization = async { authenticator.authorize("https://example.com/authorize", COMPLETION_PAGE) }
        delay(300)
        Socket("127.0.0.1", 53682).use { socket ->
            PrintWriter(socket.getOutputStream(), true).apply {
                print("GET /?code=abc123&state=deadbeef HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
                flush()
            }
            socket.getInputStream().readBytes().decodeToString()
        }.let { response ->
            assertTrue(response.contains("A Campfire csatlakozott"), "The page does not carry the title it was given.")
            assertTrue(response.contains("Bezárhatod"), "The page does not carry the message it was given.")
        }
        val outcome = withTimeout(5_000) { authorization.await() }
        assertEquals(
            expected = SyncAuthenticator.AuthorizationOutcome.Received("http://127.0.0.1:53682/?code=abc123&state=deadbeef"),
            actual = outcome
        )
        assertFalse(isPortOpen(), "The port should be released once the redirect has arrived.")
    }

    private fun isPortOpen() = try {
        Socket("127.0.0.1", 53682).close()
        true
    } catch (exception: ConnectException) {
        false
    }

    /** Only needed by the test that never starts an authorization, since [DesktopSyncAuthenticator.authorize] closes its own socket. */
    private suspend fun DesktopSyncAuthenticator.close() {
        val authorization = kotlinx.coroutines.CoroutineScope(kotlin.coroutines.EmptyCoroutineContext)
            .async { authorize("https://example.com/authorize", COMPLETION_PAGE) }
        delay(100)
        authorization.cancel()
        withTimeout(5_000) {
            while (isPortOpen()) {
                delay(50)
            }
        }
    }

    private companion object {
        /** In Hungarian on purpose: the page is rendered from what it is handed, not from anything built in. */
        val COMPLETION_PAGE = AuthorizationCompletionPage(
            title = "A Campfire csatlakozott",
            message = "Bezárhatod ezt a lapot, és visszatérhetsz az alkalmazásba."
        )
    }
}
