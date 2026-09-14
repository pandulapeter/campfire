# 21 · Desktop loopback server: no read timeout on the accepted connection, one connection ever accepted

**Severity:** medium (a stuck "waiting for the browser", a parked IO thread) · **Area:** `:data:source:remote:implementation` (desktopMain `DesktopSyncAuthenticator`)

## Cause

`SyncAuthenticator.desktop.kt:80–81`: `socket.accept().use { BufferedReader(...).readLine() }`. `soTimeout` (:53) is
set on the `ServerSocket`, which bounds `accept` only. Chrome and Firefox open a speculative second TCP connection
alongside a navigation; if the idle one is accepted first, the real redirect sits in a backlog of 1 and is never read.
`closeSocket()` closes the listener, not the accepted connection, so Cancel leaves a thread in `readLine()`.

## Fix

1. Accept in a loop until a request line that carries the redirect arrives or the overall deadline passes:

   ```kotlin
   val deadline = System.currentTimeMillis() + TIMEOUT_MILLIS
   while (true) {
       val connection = socket.accept()
       currentConnection = connection
       try {
           connection.soTimeout = CONNECTION_TIMEOUT_MILLIS   // 10 s: a browser that connected has already sent its line
           val line = BufferedReader(InputStreamReader(connection.getInputStream())).readLine().orEmpty()
           val target = line.split(' ').getOrNull(1)
           if (target != null && (target.contains("code=") || target.contains("error="))) {
               respond(connection, completionPage.toResponse())
               return Received("http://$LOOPBACK_ADDRESS:$PORT$target")
           }
           respond(connection, notFoundResponse)   // favicon.ico, a speculative connection that did send something
       } catch (exception: java.net.SocketTimeoutException) {
           // A connection that never spoke: the browser's speculative one. Back to accept.
       } finally {
           connection.close()
           currentConnection = null
       }
       if (System.currentTimeMillis() > deadline) return Cancelled("Timed out waiting for the browser.")
   }
   ```

   `respond` writes the bytes and flushes. A minimal `HTTP/1.1 404 Not Found` with `Content-Length: 0` and
   `Connection: close` is enough for the second response.
2. `closeSocket()` also closes `currentConnection` (a `@Volatile var Socket?`), so Cancel unblocks a `readLine`.
3. The `ServerSocket` backlog of 1 can stay; the loop drains it.
4. Extend the existing desktop authenticator test (there is one for cancelling a blocked `accept`): a client that
   connects and sends nothing, followed by one that sends the redirect, must yield `Received` within the connection
   timeout.
