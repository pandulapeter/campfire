package com.pandulapeter.campfire.data.source.remote.implementation.auth

import com.pandulapeter.campfire.data.source.remote.api.SyncAuthenticator
import com.pandulapeter.campfire.data.source.remote.api.model.AuthorizationCompletionPage
import java.awt.Desktop
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.scope.Scope

internal actual fun Scope.createSyncAuthenticator(): SyncAuthenticator = DesktopSyncAuthenticator()

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
internal class DesktopSyncAuthenticator(
    /**
     * Injected so that the socket half of this can be tested without a browser window opening on whoever runs the
     * tests. Cancelling a blocked `accept` is subtle enough to be worth a test of its own.
     */
    private val openInBrowser: (String) -> Unit = ::openInSystemBrowser
) : SyncAuthenticator {

    private var serverSocket: ServerSocket? = null

    override suspend fun prepareRedirectUri(): String = withContext(Dispatchers.IO) {
        // A socket left over from an abandoned attempt would hold the port this one needs.
        closeSocket()
        serverSocket = ServerSocket(PORT, 1, InetAddress.getByName(LOOPBACK_ADDRESS)).apply { soTimeout = TIMEOUT_MILLIS }
        "http://$LOOPBACK_ADDRESS:$PORT"
    }

    override suspend fun authorize(
        authorizationUrl: String,
        completionPage: AuthorizationCompletionPage
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
                // "GET /?code=... HTTP/1.1" is all that is needed; the rest of the request is not read.
                val requestLine = socket.accept().use { connection ->
                    val line = BufferedReader(InputStreamReader(connection.getInputStream())).readLine().orEmpty()
                    connection.getOutputStream().apply {
                        write(completionPage.toResponse().encodeToByteArray())
                        flush()
                    }
                    line
                }
                requestLine.split(' ').getOrNull(1)
                    ?.let { SyncAuthenticator.AuthorizationOutcome.Received("http://$LOOPBACK_ADDRESS:$PORT$it") }
                    ?: SyncAuthenticator.AuthorizationOutcome.Cancelled("The browser sent something that was not a request.")
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            SyncAuthenticator.AuthorizationOutcome.Cancelled(exception.message)
        } finally {
            // The ordinary path closes the socket through the same `finally` as the cancelled one.
            socketCloser.cancel()
        }
    }

    /** The desktop app is running throughout, so the redirect always arrives at [authorize]. */
    override suspend fun consumePendingRedirect(): String? = null

    private fun closeSocket() {
        try {
            serverSocket?.close()
        } catch (exception: Exception) {
            println("Could not close the authorization socket: ${exception.message}")
        }
        serverSocket = null
    }

    private companion object {
        const val LOOPBACK_ADDRESS = "127.0.0.1"

        /** Registered with the service as `http://127.0.0.1:53682`, character for character. */
        const val PORT = 53682
        const val TIMEOUT_MILLIS = 5 * 60 * 1000

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
