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
import java.awt.Desktop
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

/**
 * The desktop has no custom scheme to be redirected to, so it becomes a web server for the length of one
 * authorization: a socket on the loopback interface, whose address is the redirect URI. The browser delivers the
 * code to it, it answers with a page telling the user to go back to Campfire, and it closes again.
 *
 * Loopback only, and only while the browser is open: nothing outside the machine can reach it.
 *
 * The port is fixed rather than chosen by the operating system because a service only redirects to a URI that was
 * registered with it beforehand, character for character, and one that changed every run could not be registered.
 */
@Single
internal class DesktopSyncAuthenticator(
    /**
     * Injected so that the socket half of this can be tested without a browser window opening on whoever runs the
     * tests. Cancelling a blocked `accept` is subtle enough to be worth a test of its own.
     */
    private val openInBrowser: (String) -> Unit = ::openInSystemBrowser,
) : SyncAuthenticator {

    private var serverSocket: ServerSocket? = null

    /** The connection being read, so that cancelling can close it from under a blocked `readLine` as well. */
    @Volatile
    private var connection: Socket? = null

    override suspend fun prepareRedirectUri(): String = withContext(Dispatchers.IO) {
        // A socket left over from an abandoned attempt would hold the port this one needs.
        closeSocket()
        serverSocket = ServerSocket(PORT, 1, InetAddress.getByName(LOOPBACK_ADDRESS)).apply { soTimeout = TIMEOUT_MILLIS }
        "http://$LOOPBACK_ADDRESS:$PORT"
    }

    override suspend fun authorize(
        authorizationUrl: String,
        completionPage: AuthorizationCompletionPage,
    ): SyncAuthenticator.AuthorizationOutcome = coroutineScope {
        val socket = serverSocket ?: return@coroutineScope SyncAuthenticator.AuthorizationOutcome.Cancelled(
            "The authorization was not prepared."
        )
        // `accept` blocks a thread and notices neither a cancelled coroutine nor an interrupt, so cancelling has to
        // reach it the only way it can: by closing the socket underneath it, which makes it throw and the thread
        // return. This sibling coroutine is what does that - cancelling the authorization cancels it too, and its
        // `finally` runs straight away rather than waiting for the blocked thread to notice something. Without it,
        // giving up in Settings would leave the thread and the port held until the five minute timeout expired.
        val socketCloser = launch(Dispatchers.Default) {
            try {
                awaitCancellation()
            } finally {
                closeSocket()
            }
        }
        try {
            withContext(Dispatchers.IO) {
                openInBrowser(authorizationUrl)
                socket.awaitRedirect(completionPage)
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            SyncAuthenticator.AuthorizationOutcome.Cancelled(exception.toString())
        } finally {
            // The ordinary path closes the socket through the same `finally` as the cancelled one.
            socketCloser.cancel()
        }
    }

    /** The desktop app is running throughout, so the redirect always arrives at [authorize]. */
    override suspend fun consumePendingRedirect(): String? = null

    /**
     * Browsers open a speculative second connection alongside a navigation and may never write to it, and the page
     * that did arrive asks for its favicon on another. Each connection is read with a timeout of its own and
     * answered, and the listener goes back to waiting until the one that carries the redirect comes: taking the
     * first connection for the redirect would leave the real one unread in the backlog until the browser gave up on
     * the idle one.
     */
    private fun ServerSocket.awaitRedirect(completionPage: AuthorizationCompletionPage): SyncAuthenticator.AuthorizationOutcome {
        val deadline = System.currentTimeMillis() + TIMEOUT_MILLIS
        while (true) {
            val accepted = accept()
            connection = accepted
            try {
                accepted.soTimeout = CONNECTION_TIMEOUT_MILLIS
                // "GET /?code=... HTTP/1.1" is all that is needed; the rest of the request is not read.
                val target = BufferedReader(InputStreamReader(accepted.getInputStream())).readLine().orEmpty().split(' ').getOrNull(1)
                if (target != null && (target.contains("code=") || target.contains("error="))) {
                    accepted.respond(completionPage.toResponse())
                    return SyncAuthenticator.AuthorizationOutcome.Received("http://$LOOPBACK_ADDRESS:$PORT$target")
                }
                // A request that is not the redirect, or a connection that closed without one. Answering it is a
                // courtesy that may fail on a peer that has already gone, which is nothing to give up over.
                try {
                    accepted.respond(NOT_FOUND_RESPONSE)
                } catch (exception: IOException) {
                    println("Could not answer a stray authorization request: ${exception.message}")
                }
            } catch (exception: SocketTimeoutException) {
                // A connection that never spoke: the browser's speculative one.
            } finally {
                connection = null
                accepted.close()
            }
            if (System.currentTimeMillis() > deadline) {
                return SyncAuthenticator.AuthorizationOutcome.Cancelled("Timed out waiting for the browser.")
            }
        }
    }

    private fun closeSocket() {
        try {
            connection?.close()
            serverSocket?.close()
        } catch (exception: Exception) {
            println("Could not close the authorization socket: ${exception.message}")
        }
        connection = null
        serverSocket = null
    }

    private fun Socket.respond(response: String) = getOutputStream().apply {
        write(response.encodeToByteArray())
        flush()
    }

    private companion object {
        const val LOOPBACK_ADDRESS = "127.0.0.1"

        /** Registered with the service as `http://127.0.0.1:53682`, character for character. */
        const val PORT = 53682
        const val TIMEOUT_MILLIS = 5 * 60 * 1000

        /** A browser that connected has already sent its request line; one that has not in this long never will. */
        const val CONNECTION_TIMEOUT_MILLIS = 10 * 1000

        /** The answer to a request that is not the redirect, such as the favicon the completion page is asked for. */
        const val NOT_FOUND_RESPONSE = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
    }
}

/** Every desktop Campfire runs on can open a browser; the JDK just cannot always see how. */
private fun openInSystemBrowser(url: String) {
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
        Desktop.getDesktop().browse(URI(url))
    } else {
        val operatingSystem = System.getProperty("os.name").orEmpty().lowercase()
        val command = when {
            operatingSystem.contains("mac") -> arrayOf("open", url)
            operatingSystem.contains("win") -> arrayOf("rundll32", "url.dll,FileProtocolHandler", url)
            else -> arrayOf("xdg-open", url)
        }
        ProcessBuilder(*command).start()
    }
}

/**
 * The page the browser is answered with, in the language the app is in. Everything the user reads is escaped and
 * the page carries no scripts and no links: it exists to say one sentence and be closed.
 */
private fun AuthorizationCompletionPage.toResponse() = buildString {
    val body = buildString {
        append("<!doctype html><html><head><meta charset=\"utf-8\">")
        append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        append("<title>").append(title.escapedForHtml()).append("</title></head>")
        append("<body style=\"font-family: system-ui, sans-serif; text-align: center; padding: 64px 24px; color: #201a17\">")
        append("<h2>").append(title.escapedForHtml()).append("</h2>")
        append("<p>").append(message.escapedForHtml()).append("</p>")
        append("</body></html>")
    }
    append("HTTP/1.1 200 OK\r\n")
    append("Content-Type: text/html; charset=utf-8\r\n")
    // The length has to be in bytes rather than characters, or a translated page is truncated in the browser.
    append("Content-Length: ").append(body.encodeToByteArray().size).append("\r\n")
    append("Connection: close\r\n\r\n")
    append(body)
}

private fun String.escapedForHtml() = replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
