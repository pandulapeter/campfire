package com.pandulapeter.campfire.data.source.remote.implementation.auth

import com.pandulapeter.campfire.data.source.remote.api.SyncAuthenticator
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

    override suspend fun authorize(authorizationUrl: String): SyncAuthenticator.AuthorizationOutcome = coroutineScope {
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
                        write(RESPONSE.encodeToByteArray())
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

        val RESPONSE = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: text/html; charset=utf-8\r\n")
            append("Connection: close\r\n\r\n")
            append("<!doctype html><html><head><meta charset=\"utf-8\"><title>Campfire</title></head>")
            append("<body style=\"font-family: sans-serif; text-align: center; padding-top: 64px\">")
            append("<h2>Campfire is connected</h2><p>You can close this tab and go back to the app.</p>")
            append("</body></html>")
        }
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
